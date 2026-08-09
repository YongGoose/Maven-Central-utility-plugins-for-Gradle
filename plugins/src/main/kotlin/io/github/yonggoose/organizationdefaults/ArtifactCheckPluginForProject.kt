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
                val signatureCheck = validatePgpSignatures(project, errors)

                if (errors.isNotEmpty()) {
                    throw IllegalArgumentException("Validation failed:\n${errors.joinToString("\n")}")
                }

                // Report exactly how much was checked. Claiming more is the same fail-open
                // reporting this task exists to remove.
                project.logger.lifecycle(
                    when (signatureCheck) {
                        SignatureCheck.VERIFIED ->
                            "✅ ArtifactCheckPlugin: metadata and PGP signatures verified successfully."
                        SignatureCheck.PARTIAL ->
                            "✅ ArtifactCheckPlugin: metadata validation passed and the PGP signatures " +
                                "that exist were verified. Some files are unsigned (see the warnings " +
                                "above); signing is not required, so they were not treated as errors."
                        SignatureCheck.SKIPPED ->
                            "✅ ArtifactCheckPlugin: metadata validation passed. PGP signature " +
                                "verification was SKIPPED (see the warnings above) — this run does not " +
                                "confirm the artifacts are signed."
                    }
                )
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
     * Reports how much of the signature check ran, so the caller never claims a verification that
     * did not happen.
     *
     * A signature that is present but broken is always an error. A signature that is *absent* is
     * an error only when `signing.isRequired`: a build that opted out of required signing has not
     * asked for every file to be signed, and failing it would defeat the opt-out.
     */
    private fun validatePgpSignatures(project: Project, errors: MutableList<String>): SignatureCheck {
        val publishing = project.extensions.findByType(PublishingExtension::class.java)
        val signing = project.extensions.findByType(SigningExtension::class.java)

        if (publishing == null) {
            errors.add("'maven-publish' plugin not found. PGP signature verification cannot be performed.")
            return SignatureCheck.SKIPPED
        }
        // Before the `signing` check: a module with no publications is not publishing anything,
        // so demanding the signing plugin of it would be a compliance failure for a project that
        // has nothing to comply about. `maven-publish` arriving from a convention plugin while
        // the module declares no publication is a common shape.
        if (publishing.publications.isEmpty()) {
            project.logger.warn(
                "No publications found in 'publishing' extension. Skipping PGP signature verification."
            )
            return SignatureCheck.SKIPPED
        }

        if (signing == null) {
            // This is a failure, not a skip: Maven Central will not accept unsigned artifacts.
            errors.add("'signing' plugin not applied. Maven Central requires every published file to be signed.")
            return SignatureCheck.SKIPPED
        }

        val signatures = collectSignatures(project, signing)
        if (signatures.values.none { it.exists() }) {
            if (!signing.isRequired) {
                project.logger.warn(
                    "Signing is not required and nothing was signed. Skipping PGP signature verification."
                )
                return SignatureCheck.SKIPPED
            }
            errors.add("No PGP signatures were produced. Ensure the publications are configured for signing.")
            return SignatureCheck.SKIPPED
        }
        project.logger.info("Found ${signatures.size} signature(s) for ${signatures.keys.map { it.name }.sorted()}")

        val context = SignatureCheckContext(project, signatures, signing.isRequired, errors)
        publishing.publications.withType(MavenPublication::class.java).forEach { publication ->
            validateMavenPublicationSignatures(context, publication)
        }

        if (context.verified == 0) {
            if (context.unsigned == 0) {
                // `publications` can be non-empty while holding no MavenPublication at all -- an
                // Ivy-only build, say. Claiming the signatures passed here would be the same
                // fail-open reporting this task exists to remove.
                project.logger.warn(
                    "No Maven publication files were inspected. Skipping PGP signature verification."
                )
            }
            // Otherwise files *were* inspected and none of them carried a signature. The per-file
            // warnings already said which; saying "nothing was inspected" on top would be wrong.
            return SignatureCheck.SKIPPED
        }
        return if (context.unsigned == 0) SignatureCheck.VERIFIED else SignatureCheck.PARTIAL
    }

    /** How much of the signature check actually happened, so the task can report it truthfully. */
    private enum class SignatureCheck { VERIFIED, PARTIAL, SKIPPED }

    /**
     * Everything the per-file signature check needs, plus its running tally. Carrying it as one
     * object keeps the call sites readable -- threading five invariant arguments through every
     * file was worse.
     */
    private class SignatureCheckContext(
        val project: Project,
        val signatures: Map<File, File>,
        val signingRequired: Boolean,
        val errors: MutableList<String>
    ) {
        /** Files whose signature was found and parsed. */
        var verified: Int = 0

        /** Files with no signature, tolerated because signing is not required. */
        var unsigned: Int = 0
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

    private fun validateMavenPublicationSignatures(
        context: SignatureCheckContext,
        publication: MavenPublication
    ) {
        context.project.logger.info("Validating PGP signatures for publication: ${publication.name}")

        publication.artifacts.forEach { artifact ->
            verifyFileSignature(context, publication, artifact.file, "artifact")
        }

        // Gradle Module Metadata is published and signed alongside the POM, so a missing
        // module.json signature is rejected at upload just like a missing POM signature.
        val moduleFile = findModuleMetadataFile(context.project, publication)
        if (moduleFile != null) {
            verifyFileSignature(context, publication, moduleFile, "module metadata")
        }

        val pomFile = findPomFile(context.project, publication)
        if (pomFile == null) {
            context.errors.add(
                "POM file for publication '${publication.name}' was not found, so its signature " +
                    "could not be verified. Run '${pomTaskNameFor(publication)}' first."
            )
            return
        }

        verifyFileSignature(context, publication, pomFile, "POM")
    }

    private fun verifyFileSignature(
        context: SignatureCheckContext,
        publication: MavenPublication,
        file: File,
        kind: String
    ) {
        // Every publication writes its POM to build/publications/<name>/pom-default.xml, so the
        // file name alone cannot tell two publications apart in a report. Name both.
        val subject = "$kind of publication '${publication.name}' at '${file.path}'"

        if (!file.exists()) {
            // A safety net rather than an expected path: the task depends on the publication's
            // artifacts, so they should already exist by the time this runs.
            context.errors.add("$subject has not been built, so its signature could not be checked.")
            return
        }

        val signatureFile = context.signatures[file.absoluteFile]

        if (signatureFile == null || !signatureFile.exists()) {
            val problem = if (signatureFile == null) {
                "No PGP signature is registered for $subject."
            } else {
                "The PGP signature for $subject was never produced (expected it at '${signatureFile.path}')."
            }

            if (context.signingRequired) {
                context.errors.add("$problem Maven Central requires every published file to be signed.")
            } else {
                // `setRequired(false)` opted out of signing everything, so an absent signature is
                // the user's choice. A *broken* one below still fails: that is never intentional.
                context.project.logger.warn("$problem Signing is not required, so this is not an error.")
                context.unsigned++
            }
            return
        }

        val result = PgpSignatureVerifier.verify(file, signatureFile)
        if (result.isOk) {
            context.project.logger.info("PGP signature verified for $kind ${file.name}. ${result.detail}")
            context.verified++
        } else {
            // A broken signature fails regardless of `isRequired`: nobody opts into those.
            context.errors.add("PGP signature verification FAILED for $subject: ${result.detail}")
        }
    }

    /**
     * The generated Gradle Module Metadata for [publication], or `null` when there is none to
     * check.
     *
     * Requires both that the task is enabled and that the file is there, which covers
     * `enabled = false`. It does **not** cover a task skipped by an `onlyIf` — Gradle disables
     * module metadata that way for publications that cannot carry it — so a `build/` directory
     * left over from when the publication *could* still holds a `module.json` that this will ask
     * for a signature of. `./gradlew clean` clears it; there is no public API that reports an
     * `onlyIf` verdict before execution.
     */
    private fun findModuleMetadataFile(project: Project, publication: MavenPublication): File? {
        val taskName = "generateMetadataFileFor${capitalize(publication.name)}Publication"
        val task = project.tasks.withType(GenerateModuleMetadata::class.java)
            .findByName(taskName)
            ?.takeIf { it.enabled }
            ?: return null
        return task.outputFile.orNull?.asFile?.takeIf { it.exists() }
    }

    /**
     * Resolves the generated POM for [publication] from its `GenerateMavenPom` task, falling back
     * to the conventional output location. Returns `null` when the POM has not been generated.
     */
    private fun findPomFile(project: Project, publication: MavenPublication): File? {
        val destination = project.tasks.withType(GenerateMavenPom::class.java)
            .findByName(pomTaskNameFor(publication))
            ?.destination
        if (destination != null && destination.exists()) {
            return destination
        }

        val conventional = File(
            project.layout.buildDirectory.get().asFile,
            "publications/${publication.name}/pom-default.xml"
        )
        return conventional.takeIf { it.exists() }
    }

    private fun pomTaskNameFor(publication: MavenPublication): String =
        "$GENERATE_POM_TASK_PREFIX${capitalize(publication.name)}Publication"

    private fun capitalize(value: String): String = value.replaceFirstChar { it.uppercaseChar() }

    companion object {
        private const val TASK_NAME = "checkProjectArtifact"
        private const val GENERATE_POM_TASK_PREFIX = "generatePomFileFor"
    }
}
