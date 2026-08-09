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
            dependsOn(project.tasks.withType(Sign::class.java))
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
                validatePgpSignatures(project, errors)

                if (errors.isNotEmpty()) {
                    throw IllegalArgumentException("Validation failed:\n${errors.joinToString("\n")}")
                }

                project.logger.lifecycle(
                    "✅ ArtifactCheckPlugin: All validations including PGP signature verification passed successfully."
                )
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
            project.logger.info(
                "No 'mergedDefaults' on '${project.path}'; falling back to the root project's metadata."
            )
            return rootExtras.get(key) as? OrganizationDefaults
        }

        return null
    }

    private fun validatePgpSignatures(project: Project, errors: MutableList<String>) {
        val publishing = project.extensions.findByType(PublishingExtension::class.java)
        val signing = project.extensions.findByType(SigningExtension::class.java)

        if (publishing == null) {
            errors.add("'maven-publish' plugin not found. PGP signature verification cannot be performed.")
            return
        }
        if (signing == null) {
            errors.add("'signing' plugin is not configured to sign publications. Verification skipped.")
            return
        }

        if (!signing.isRequired) {
            project.logger.warn("Signing is not required. Skipping PGP signature verification.")
            return
        }

        if (publishing.publications.isEmpty()) {
            project.logger.warn(
                "No publications found in 'publishing' extension. Skipping PGP signature verification."
            )
            return
        }

        val signatureArtifacts = signing.configuration.artifacts
        if (signatureArtifacts.isEmpty()) {
            errors.add("No artifacts found to verify PGP signatures. Ensure artifacts are configured for signing.")
            return
        }

        val signatureFiles: List<File> = signatureArtifacts.map { it.file }
        project.logger.info("Found ${signatureFiles.size} signature file(s): ${signatureFiles.map { it.name }.sorted()}")

        publishing.publications.withType(MavenPublication::class.java).forEach { publication ->
            validateMavenPublicationSignatures(project, publication, signatureFiles, errors)
        }
    }

    private fun validateMavenPublicationSignatures(
        project: Project,
        publication: MavenPublication,
        signatureFiles: List<File>,
        errors: MutableList<String>
    ) {
        project.logger.info("Validating PGP signatures for publication: ${publication.name}")

        publication.artifacts.forEach { artifact ->
            verifyFileSignature(project, artifact.file, signatureFiles, "artifact", errors)
        }

        val pomFile = findPomFile(project, publication)
        if (pomFile == null) {
            val pomTaskName = pomTaskNameFor(publication)
            errors.add(
                "POM file for publication '${publication.name}' was not found, so its signature " +
                    "could not be verified. Run '$pomTaskName' first."
            )
            return
        }

        verifyFileSignature(project, pomFile, signatureFiles, "POM", errors)
    }

    private fun verifyFileSignature(
        project: Project,
        file: File,
        signatureFiles: List<File>,
        kind: String,
        errors: MutableList<String>
    ) {
        val signatureFile = resolveSignature(file, signatureFiles)

        if (signatureFile == null) {
            val expected = expectedSignatureFor(file)
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
     * Finds the signature that belongs to [file], matching on the full path rather than the file
     * name.
     *
     * Every `MavenPublication` writes its POM to `build/publications/<name>/pom-default.xml`, so
     * in a build with more than one publication — `java-gradle-plugin` alone adds a marker
     * publication per declared plugin — a name-keyed lookup collapses every `pom-default.xml.asc`
     * onto a single entry and pairs the remaining POMs with another publication's signature.
     *
     * A bare name match is still accepted as a fallback for signing setups that write signatures
     * somewhere other than beside the file, but only when it is unambiguous: pairing the wrong
     * files is the failure mode this whole check exists to prevent.
     */
    private fun resolveSignature(file: File, signatureFiles: List<File>): File? {
        val expected = expectedSignatureFor(file)
        signatureFiles.firstOrNull { it.absoluteFile == expected }?.let { return it }

        return signatureFiles.filter { it.name == expected.name }.singleOrNull()
    }

    private fun expectedSignatureFor(file: File): File =
        File(file.absoluteFile.parentFile, PgpSignatureVerifier.signatureNameFor(file))

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
