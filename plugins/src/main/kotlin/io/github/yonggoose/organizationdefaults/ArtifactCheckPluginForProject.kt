package io.github.yonggoose.organizationdefaults

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import java.io.File

/**
 * A Gradle plugin that adds an artifact verification task to the project.
 * Validates the metadata of artifacts to be published according to Maven Central requirements
 * and verifies local PGP signatures using Bouncy Castle.
 *
 * The rules themselves live in [MavenCentralMetadataValidator] and [PgpSignatureVerifier]; this
 * class only wires them to a Gradle task.
 */
class ArtifactCheckPluginForProject : Plugin<Project> {

    override fun apply(project: Project) {
        project.tasks.register(TASK_NAME) {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description =
                "Verifies that all artifacts staged for publishing are signed and meet Maven Central requirements."

            // The signatures and the POM only exist on disk once their producing tasks have run.
            //
            // The Sign dependency is conditional: with no signatory configured, Gradle fails those
            // tasks with "no configured signatory", and depending on them unconditionally would
            // mean a contributor without GPG keys never sees the metadata report at all. Without
            // a signatory the task still runs and reports the signatures as missing.
            dependsOn(
                project.provider {
                    val signing = project.extensions.findByType(SigningExtension::class.java)
                    if (signing?.signatory != null) {
                        project.tasks.withType(Sign::class.java).toList()
                    } else {
                        emptyList()
                    }
                }
            )
            dependsOn(project.tasks.withType(GenerateMavenPom::class.java))

            doLast {
                val pom = resolveMergedDefaults(project)
                    ?: throw IllegalStateException(
                        "No merged POM metadata found for '${project.path}'. Apply " +
                            "'io.github.yonggoose.maven.central.utility.plugin.project' to this project " +
                            "and configure 'rootProjectPom' / 'projectPom'."
                    )

                val errors = mutableListOf<String>()

                errors.addAll(MavenCentralMetadataValidator.validate(pom))
                val signaturesInspected = validatePgpSignatures(project, errors)

                if (errors.isNotEmpty()) {
                    throw IllegalArgumentException("Validation failed:\n${errors.joinToString("\n")}")
                }

                // Only claim the signatures passed when some were actually looked at. Saying
                // otherwise is the same fail-open reporting this task exists to remove.
                if (signaturesInspected) {
                    project.logger.lifecycle(
                        "✅ ArtifactCheckPlugin: metadata and PGP signatures verified successfully."
                    )
                } else {
                    project.logger.lifecycle(
                        "✅ ArtifactCheckPlugin: metadata validation passed. PGP signature " +
                            "verification was SKIPPED (see the warnings above) — this run does not " +
                            "confirm the artifacts are signed."
                    )
                }
            }
        }
    }

    /**
     * Resolves the merged POM metadata for [project].
     *
     * The project plugin writes `mergedDefaults` into every project it is applied to, so the
     * project's own entry is authoritative and must win over the root project's — otherwise a
     * submodule would be validated against the organization defaults it just overrode.
     * The root project is only consulted as a fallback.
     */
    private fun resolveMergedDefaults(project: Project): OrganizationDefaults? {
        val key = OrganizationDefaultsProjectPlugin.MERGED_DEFAULTS_PROPERTY

        val ownExtras = project.extensions.extraProperties
        if (ownExtras.has(key)) {
            val own = ownExtras.get(key)
            if (own is OrganizationDefaults) {
                return own
            }
        }

        val rootExtras = project.rootProject.extensions.extraProperties
        if (rootExtras.has(key)) {
            // Loud on purpose: this validates a different project's coordinates than the one the
            // task was invoked on, which is precisely the confusion this lookup was fixed to avoid.
            project.logger.warn(
                "No 'mergedDefaults' on '${project.path}', so it is being validated against the " +
                    "root project's metadata. Apply " +
                    "'io.github.yonggoose.maven.central.utility.plugin.project' to '${project.path}' " +
                    "to validate its own coordinates."
            )
            return rootExtras.get(key) as? OrganizationDefaults
        }

        return null
    }

    /**
     * Returns `true` when signatures were actually inspected, so the caller can avoid reporting a
     * signature check that never ran.
     */
    private fun validatePgpSignatures(project: Project, errors: MutableList<String>): Boolean {
        val publishing = project.extensions.findByType(PublishingExtension::class.java)
        val signing = project.extensions.findByType(SigningExtension::class.java)

        if (publishing == null) {
            errors.add("'maven-publish' plugin not found. PGP signature verification cannot be performed.")
            return false
        }
        if (signing == null) {
            errors.add("'signing' plugin is not configured to sign publications. Verification skipped.")
            return false
        }

        if (!signing.isRequired) {
            project.logger.warn("Signing is not required. Skipping PGP signature verification.")
            return false
        }

        if (publishing.publications.isEmpty()) {
            project.logger.warn(
                "No publications found in 'publishing' extension. Skipping PGP signature verification."
            )
            return false
        }

        val signatureFiles = collectSignatureFiles(project, signing)
        if (signatureFiles.isEmpty()) {
            errors.add("No artifacts found to verify PGP signatures. Ensure artifacts are configured for signing.")
            return false
        }
        project.logger.info("Found ${signatureFiles.size} signature file(s): ${signatureFiles.map { it.name }.sorted()}")

        var filesInspected = 0
        publishing.publications.withType(MavenPublication::class.java).forEach { publication ->
            filesInspected += validateMavenPublicationSignatures(project, publication, signatureFiles, errors)
        }

        if (filesInspected == 0) {
            // `publications` can be non-empty while holding no MavenPublication at all -- an
            // Ivy-only build, say. Claiming the signatures passed here would be the same
            // fail-open reporting this task exists to remove.
            project.logger.warn(
                "No Maven publication files were inspected. Skipping PGP signature verification."
            )
            return false
        }
        return true
    }

    /**
     * Gathers every signature this project produces.
     *
     * `signing.configuration.artifacts` only carries signatures registered through `sign(Task)` or
     * `sign(Configuration)`. `sign(publishing.publications)` — the setup Maven Central publishers
     * actually use — attaches its signatures to the publication as derived artifacts and leaves
     * the `signatures` configuration empty, so reading the configuration alone made this check
     * report "No artifacts found to verify PGP signatures" for precisely the case it exists to
     * cover. Take the `Sign` tasks' own outputs as well.
     */
    private fun collectSignatureFiles(project: Project, signing: SigningExtension): List<File> {
        val files = LinkedHashSet<File>()
        signing.configuration.artifacts.forEach { files.add(it.file) }
        project.tasks.withType(Sign::class.java).forEach { files.addAll(it.signatureFiles.files) }
        return files.toList()
    }

    /** Returns how many files were actually examined, so the caller can tell a no-op apart. */
    private fun validateMavenPublicationSignatures(
        project: Project,
        publication: MavenPublication,
        signatureFiles: List<File>,
        errors: MutableList<String>
    ): Int {
        project.logger.info("Validating PGP signatures for publication: ${publication.name}")

        var inspected = 0

        publication.artifacts.forEach { artifact ->
            verifyFileSignature(project, artifact.file, signatureFiles, "artifact", errors)
            inspected++
        }

        val pomFile = findPomFile(project, publication)
        if (pomFile == null) {
            val pomTaskName = pomTaskNameFor(publication)
            errors.add(
                "POM file for publication '${publication.name}' was not found, so its signature " +
                    "could not be verified. Run '$pomTaskName' first."
            )
            return inspected
        }

        verifyFileSignature(project, pomFile, signatureFiles, "POM", errors)
        return inspected + 1
    }

    private fun verifyFileSignature(
        project: Project,
        file: File,
        signatureFiles: List<File>,
        kind: String,
        errors: MutableList<String>
    ) {
        val signatureFile = PgpSignatureVerifier.resolveSignatureFor(file, signatureFiles)

        if (signatureFile == null) {
            val expected = PgpSignatureVerifier.expectedSignatureFor(file)
            errors.add("PGP signature not found for $kind '${file.name}' (expected '${expected.path}').")
            return
        }

        val result = PgpSignatureVerifier.verify(file, signatureFile)
        if (result.isOk) {
            project.logger.info("PGP signature verified for $kind ${file.name}. ${result.detail}")
        } else {
            errors.add("PGP signature verification FAILED for $kind '${file.name}': ${result.detail}")
        }
    }

    /**
     * Resolves the generated POM for [publication] from its `GenerateMavenPom` task, falling back
     * to the conventional output location. Returns `null` when the POM has not been generated.
     */
    private fun findPomFile(project: Project, publication: MavenPublication): File? {
        val taskName = pomTaskNameFor(publication)
        val destination = project.tasks.withType(GenerateMavenPom::class.java).findByName(taskName)?.destination
        if (destination != null && destination.exists()) {
            return destination
        }

        val conventional = File(
            project.layout.buildDirectory.get().asFile,
            "publications/${publication.name}/pom-default.xml"
        )
        return conventional.takeIf { it.exists() }
    }

    private fun pomTaskNameFor(publication: MavenPublication): String {
        val capitalized = publication.name.replaceFirstChar { it.uppercaseChar() }
        return "$GENERATE_POM_TASK_PREFIX${capitalized}Publication"
    }

    companion object {
        private const val TASK_NAME = "checkProjectArtifact"
        private const val GENERATE_POM_TASK_PREFIX = "generatePomFileFor"
    }
}
