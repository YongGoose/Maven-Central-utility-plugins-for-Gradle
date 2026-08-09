package io.github.yonggoose.organizationdefaults

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata
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
            dependsOn(project.tasks.withType(GenerateModuleMetadata::class.java))

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
     * Resolves the merged POM metadata for [project], and only for [project].
     *
     * The project plugin writes `mergedDefaults` into every project it is applied to, so a project
     * that has no entry of its own has nothing to validate. There is deliberately no fallback to
     * the root project: reporting success for `:sub` after checking the root's artifactId, version
     * and scm is the confusion this task was fixed to stop, and a warning is too easy to miss in
     * CI output. Applying the project plugin to `:sub` gives it the root's values through the
     * normal merge, explicitly.
     */
    private fun resolveMergedDefaults(project: Project): OrganizationDefaults? {
        val extras = project.extensions.extraProperties
        val key = OrganizationDefaultsProjectPlugin.MERGED_DEFAULTS_PROPERTY

        if (!extras.has(key)) {
            return null
        }
        return extras.get(key) as? OrganizationDefaults
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
            // This is a failure, not a skip: Maven Central will not accept unsigned artifacts.
            errors.add("'signing' plugin not applied. Maven Central requires every published file to be signed.")
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

        // Gradle Module Metadata is published and signed alongside the POM, so a missing
        // module.json.asc is rejected at upload just like a missing pom.asc. It is optional --
        // GenerateModuleMetadata can be disabled -- so it is only checked when it was produced.
        val moduleFile = findModuleMetadataFile(project, publication)
        if (moduleFile != null) {
            verifyFileSignature(project, moduleFile, signatureFiles, "module metadata", errors)
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
        if (!file.exists()) {
            // Say what actually went wrong. Without a signatory the Sign tasks are left out of
            // the graph, and with them the tasks that would have built this file, so blaming a
            // missing signature here would point at the wrong thing.
            errors.add(
                "$kind '${file.name}' has not been built, so its signature could not be checked " +
                    "(expected the file at '${file.path}')."
            )
            return
        }

        val signatureFile = PgpSignatureVerifier.resolveSignatureFor(file, signatureFiles)

        if (signatureFile == null) {
            val expected = PgpSignatureVerifier.expectedSignatureFor(file)
            val sameName = signatureFiles.filter { it.name == expected.name }

            // Distinguish "nothing signed this" from "several candidates and none beside the
            // file": in the ambiguous case the signatures do exist, and pointing at the sibling
            // path would send the reader looking for a file that was never going to be there.
            if (sameName.size > 1) {
                errors.add(
                    "PGP signature for $kind '${file.name}' is ambiguous: nothing at " +
                        "'${expected.path}', and ${sameName.size} candidates share the name " +
                        "'${expected.name}' (${sameName.joinToString { it.path }})."
                )
            } else {
                errors.add("PGP signature not found for $kind '${file.name}' (expected '${expected.path}').")
            }
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

    /** The generated Gradle Module Metadata for [publication], or `null` if it was not produced. */
    private fun findModuleMetadataFile(project: Project, publication: MavenPublication): File? {
        val taskName = "generateMetadataFileFor${capitalize(publication.name)}Publication"
        val task = project.tasks.withType(GenerateModuleMetadata::class.java).findByName(taskName)
        return task?.outputFile?.orNull?.asFile?.takeIf { it.exists() }
    }

    private fun pomTaskNameFor(publication: MavenPublication): String =
        "$GENERATE_POM_TASK_PREFIX${capitalize(publication.name)}Publication"

    private fun capitalize(value: String): String = value.replaceFirstChar { it.uppercaseChar() }

    companion object {
        private const val TASK_NAME = "checkProjectArtifact"
        private const val GENERATE_POM_TASK_PREFIX = "generatePomFileFor"
    }
}
