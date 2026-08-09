package io.github.yonggoose.organizationdefaults

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.Signature
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

            // The signatures, the POM and the module metadata only exist on disk once their
            // producing tasks have run.
            //
            // This depends on Sign unconditionally. An earlier revision gated it on
            // `signing.signatory != null` so that a contributor without keys still got the
            // metadata report, but that predicate does not hold: GnupgSignatoryProvider
            // (`useGpgCmd()`) always returns a signatory whether or not a secret key exists, and
            // PgpSignatoryProvider *builds* one on access — reading and parsing the key ring, and
            // throwing during task-graph resolution if the configured path is unusable, before
            // any report could be printed. `signing { setRequired(false) }` is the supported way
            // to run the metadata checks without keys.
            dependsOn(project.tasks.withType(Sign::class.java))
            dependsOn(project.tasks.withType(GenerateMavenPom::class.java))
            dependsOn(project.tasks.withType(GenerateModuleMetadata::class.java))

            // And on the artifacts themselves: signing them does not, on its own, guarantee they
            // are in the graph, and an artifact that was never built cannot be checked.
            dependsOn(
                project.provider {
                    project.extensions.findByType(PublishingExtension::class.java)
                        ?.publications
                        ?.withType(MavenPublication::class.java)
                        ?.flatMap { it.artifacts }
                        ?: emptyList()
                }
            )

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

        val value = extras.get(key)
        if (value is OrganizationDefaults) {
            return value
        }
        // Not "the plugin is missing" -- it ran and left something unrecognisable, which in
        // practice means two versions of it on the build classpath under different classloaders.
        throw IllegalStateException(
            "'$key' on '${project.path}' holds a ${value?.javaClass?.name}, not an " +
                "${OrganizationDefaults::class.java.name}. Check for more than one version of " +
                "'io.github.yonggoose.maven.central.utility.plugin.project' on the build classpath."
        )
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

        if (publishing.publications.isEmpty()) {
            project.logger.warn(
                "No publications found in 'publishing' extension. Skipping PGP signature verification."
            )
            return false
        }

        val signatures = collectSignatures(project, signing)
        if (signatures.values.none { it.exists() }) {
            // Gate on what was produced rather than on `isRequired`: a build that sets
            // setRequired(false) but does have a signatory still signs everything, and throwing
            // that verdict away would under-report.
            if (!signing.isRequired) {
                project.logger.warn(
                    "Signing is not required and nothing was signed. Skipping PGP signature verification."
                )
                return false
            }
            errors.add("No PGP signatures were produced. Ensure the publications are configured for signing.")
            return false
        }
        project.logger.info("Found ${signatures.size} signature(s) for ${signatures.keys.map { it.name }.sorted()}")

        var filesInspected = 0
        publishing.publications.withType(MavenPublication::class.java).forEach { publication ->
            filesInspected += validateMavenPublicationSignatures(project, publication, signatures, errors)
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
     * Maps each signed file to its signature, using the pairing Gradle already records.
     *
     * Reading `Signature.toSign` is what makes this robust. Deriving the signature path instead —
     * "the sibling named `<file>.asc`" — assumes an extension that `signing.signatureType` can
     * change (a `BinarySignatureType` build emits `.sig`), and re-derives something Gradle knows
     * exactly. It also removes the whole class of wrong-file pairing by construction: a signature
     * can only ever be attributed to the file Gradle signed.
     *
     * Both sources are needed. `signing.configuration.artifacts` only carries signatures
     * registered through `sign(Task)` / `sign(Configuration)`; `sign(publishing.publications)` —
     * the setup Maven Central publishers actually use — attaches them to the publication and
     * leaves that configuration empty.
     */
    private fun collectSignatures(project: Project, signing: SigningExtension): Map<File, File> {
        val bySignedFile = LinkedHashMap<File, File>()

        fun record(signature: Signature) {
            val signed = signature.toSign ?: return
            val signatureFile = signature.file ?: return
            bySignedFile[signed.absoluteFile] = signatureFile
        }

        signing.configuration.artifacts.filterIsInstance<Signature>().forEach(::record)
        project.tasks.withType(Sign::class.java).forEach { task -> task.signatures.forEach(::record) }

        return bySignedFile
    }

    /** Returns how many files were actually examined, so the caller can tell a no-op apart. */
    private fun validateMavenPublicationSignatures(
        project: Project,
        publication: MavenPublication,
        signatures: Map<File, File>,
        errors: MutableList<String>
    ): Int {
        project.logger.info("Validating PGP signatures for publication: ${publication.name}")

        var inspected = 0

        publication.artifacts.forEach { artifact ->
            verifyFileSignature(project, artifact.file, signatures, "artifact", errors)
            inspected++
        }

        // Gradle Module Metadata is published and signed alongside the POM, so a missing
        // module.json.asc is rejected at upload just like a missing pom.asc. It is optional --
        // GenerateModuleMetadata can be disabled -- so it is only checked when it was produced.
        val moduleFile = findModuleMetadataFile(project, publication)
        if (moduleFile != null) {
            verifyFileSignature(project, moduleFile, signatures, "module metadata", errors)
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

        verifyFileSignature(project, pomFile, signatures, "POM", errors)
        return inspected + 1
    }

    private fun verifyFileSignature(
        project: Project,
        file: File,
        signatures: Map<File, File>,
        kind: String,
        errors: MutableList<String>
    ) {
        if (!file.exists()) {
            // A safety net rather than an expected path: the task depends on the publication's
            // artifacts, so they should already exist by the time this runs.
            errors.add(
                "$kind '${file.name}' has not been built, so its signature could not be checked " +
                    "(expected the file at '${file.path}')."
            )
            return
        }

        val signatureFile = signatures[file.absoluteFile]

        if (signatureFile == null) {
            errors.add(
                "No PGP signature is registered for $kind '${file.name}'. Maven Central requires " +
                    "every published file to be signed."
            )
            return
        }

        if (!signatureFile.exists()) {
            errors.add(
                "PGP signature for $kind '${file.name}' was never produced " +
                    "(expected it at '${signatureFile.path}')."
            )
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

    /**
     * The generated Gradle Module Metadata for [publication], or `null` if it is not part of the
     * publication.
     *
     * The task's `enabled` flag is what decides that, not the file: disabling
     * `GenerateModuleMetadata` on a project that published with it before leaves a stale
     * `module.json` in `build/`, and keying off mere existence would then demand a signature for
     * a file that is no longer published and therefore never signed.
     */
    private fun findModuleMetadataFile(project: Project, publication: MavenPublication): File? {
        val taskName = "generateMetadataFileFor${capitalize(publication.name)}Publication"
        val task = project.tasks.withType(GenerateModuleMetadata::class.java)
            .findByName(taskName)
            ?.takeIf { it.enabled }
            ?: return null
        return task.outputFile.orNull?.asFile?.takeIf { it.exists() }
    }

    private fun pomTaskNameFor(publication: MavenPublication): String =
        "$GENERATE_POM_TASK_PREFIX${capitalize(publication.name)}Publication"

    private fun capitalize(value: String): String = value.replaceFirstChar { it.uppercaseChar() }

    companion object {
        private const val TASK_NAME = "checkProjectArtifact"
        private const val GENERATE_POM_TASK_PREFIX = "generatePomFileFor"
    }
}
