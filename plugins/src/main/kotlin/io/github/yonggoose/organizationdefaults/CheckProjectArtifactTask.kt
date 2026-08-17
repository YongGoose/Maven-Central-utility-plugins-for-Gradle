package io.github.yonggoose.organizationdefaults

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.Serializable

/**
 * One Maven publication's files, as [ArtifactCheckPluginForProject] resolved them at configuration
 * time.
 *
 * Paths only, never existence: the POM, the module metadata and the artifacts are all written
 * during execution, so nothing here can be checked for being on disk until the task runs. A `null`
 * [pomFile] or [moduleMetadataFile] means something stronger — the task that would have produced
 * it is not part of this build, so whatever is at that path belongs to an earlier one.
 */
data class PublicationArtifacts(
    val name: String,
    val artifacts: List<File>,
    val pomTaskName: String,
    val pomFile: File?,
    val moduleMetadataFile: File?
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Verifies that everything staged for publishing is signed and meets Maven Central's requirements.
 *
 * Every input is captured at configuration time by [ArtifactCheckPluginForProject], and the task
 * holds no reference to `Project`. That is what makes it usable with the configuration cache; the
 * previous `doLast` block read the merged metadata, both extensions, the `Sign` task list and
 * another task's `state` at execution time, and Gradle will not serialize a task action holding a
 * `Project`.
 *
 * The properties are `@Internal` rather than `@Input`/`@InputFiles` on purpose. This task declares
 * no outputs, so Gradle runs it on every invocation whatever its inputs say; annotating the paths
 * as inputs would suggest an up-to-date check that does not exist, and would make the report's
 * absolute paths part of a fingerprint nothing consumes.
 */
@DisableCachingByDefault(because = "A verification task with no outputs; its job is to report on every run")
abstract class CheckProjectArtifactTask : DefaultTask() {

    /** Only for messages — the task must not reach for a `Project` to name itself. */
    @get:Internal
    abstract val projectPath: Property<String>

    /** Unset when the project plugin left no `mergedDefaults`, which is an error, not an empty POM. */
    @get:Internal
    abstract val mergedDefaults: Property<OrganizationDefaults>

    @get:Internal
    abstract val publishingApplied: Property<Boolean>

    @get:Internal
    abstract val signingApplied: Property<Boolean>

    @get:Internal
    abstract val signingRequired: Property<Boolean>

    /** All publications, not just the Maven ones, so "none at all" stays distinguishable. */
    @get:Internal
    abstract val publicationCount: Property<Int>

    @get:Internal
    abstract val publications: ListProperty<PublicationArtifacts>

    /** Signed file (absolute) to the signature over it, as the `signing` plugin itself records it. */
    @get:Internal
    abstract val signaturesBySignedFile: MapProperty<File, File>

    @TaskAction
    fun check() {
        val pom = mergedDefaults.orNull
            ?: throw IllegalStateException(
                "No merged POM metadata found for '${projectPath.get()}'. Apply " +
                    "'io.github.yonggoose.maven.central.utility.plugin.project' to this project " +
                    "and configure 'rootProjectPom' / 'projectPom'."
            )

        val errors = mutableListOf<String>()

        errors.addAll(MavenCentralMetadataValidator.validate(pom))
        val signatureCheck = validatePgpSignatures(errors)

        if (errors.isNotEmpty()) {
            throw IllegalArgumentException("Validation failed:\n${errors.joinToString("\n")}")
        }

        // Report exactly how much was checked. Claiming more is the same fail-open reporting this
        // task exists to remove.
        logger.lifecycle(
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

    /**
     * Reports how much of the signature check ran, so the caller never claims a verification that
     * did not happen.
     *
     * A signature that is present but broken is always an error. A signature that is *absent* is
     * an error only when signing is required: a build that opted out of required signing has not
     * asked for every file to be signed, and failing it would defeat the opt-out.
     */
    private fun validatePgpSignatures(errors: MutableList<String>): SignatureCheck {
        if (!publishingApplied.get()) {
            errors.add("'maven-publish' plugin not found. PGP signature verification cannot be performed.")
            return SignatureCheck.SKIPPED
        }
        // Before the `signing` check: a module with no publications is not publishing anything, so
        // demanding the signing plugin of it would be a compliance failure for a project that has
        // nothing to comply about. `maven-publish` arriving from a convention plugin while the
        // module declares no publication is a common shape.
        if (publicationCount.get() == 0) {
            logger.warn("No publications found in 'publishing' extension. Skipping PGP signature verification.")
            return SignatureCheck.SKIPPED
        }

        if (!signingApplied.get()) {
            // This is a failure, not a skip: Maven Central will not accept unsigned artifacts.
            errors.add("'signing' plugin not applied. Maven Central requires every published file to be signed.")
            return SignatureCheck.SKIPPED
        }

        val signatures = signaturesBySignedFile.get()
        val required = signingRequired.get()

        if (signatures.values.none { it.exists() }) {
            if (!required) {
                logger.warn("Signing is not required and nothing was signed. Skipping PGP signature verification.")
                return SignatureCheck.SKIPPED
            }
            errors.add("No PGP signatures were produced. Ensure the publications are configured for signing.")
            return SignatureCheck.SKIPPED
        }
        logger.info("Found ${signatures.size} signature(s) for ${signatures.keys.map { it.name }.sorted()}")

        val tally = Tally()
        publications.get().forEach { publication ->
            validatePublicationSignatures(publication, signatures, required, errors, tally)
        }

        if (tally.inspected == 0) {
            // `publications` can be non-empty while holding no MavenPublication at all -- an
            // Ivy-only build, say. Claiming the signatures passed here would be the same fail-open
            // reporting this task exists to remove.
            logger.warn("No Maven publication files were inspected. Skipping PGP signature verification.")
            return SignatureCheck.SKIPPED
        }
        // Counted separately from verified/unsigned: files can also be inspected and rejected, and
        // inferring "nothing was inspected" from those two would contradict the errors.
        if (tally.verified == 0) {
            return SignatureCheck.SKIPPED
        }
        return if (tally.unsigned == 0) SignatureCheck.VERIFIED else SignatureCheck.PARTIAL
    }

    private fun validatePublicationSignatures(
        publication: PublicationArtifacts,
        signatures: Map<File, File>,
        required: Boolean,
        errors: MutableList<String>,
        tally: Tally
    ) {
        logger.info("Validating PGP signatures for publication: ${publication.name}")

        publication.artifacts.forEach { artifact ->
            verifyFileSignature(publication, artifact, "artifact", signatures, required, errors, tally)
        }

        // Gradle Module Metadata is published and signed alongside the POM, so a missing
        // module.json signature is rejected at upload just like a missing POM signature. A file
        // that is on disk without its producing task being in this build is an earlier build's.
        publication.moduleMetadataFile
            ?.takeIf { it.exists() }
            ?.let { verifyFileSignature(publication, it, "module metadata", signatures, required, errors, tally) }

        val pomFile = publication.pomFile?.takeIf { it.exists() }
        if (pomFile == null) {
            // Not "run the task first": this task depends on every GenerateMavenPom, so the only
            // way to get here is a POM task that was disabled or excluded from this build. Telling
            // the user to run what they just opted out of would send them in a circle.
            errors.add(
                "No POM was generated for publication '${publication.name}', so its signature " +
                    "could not be verified. '${publication.pomTaskName}' was disabled or " +
                    "excluded from this build; Maven Central requires a POM for every artifact."
            )
            return
        }

        verifyFileSignature(publication, pomFile, "POM", signatures, required, errors, tally)
    }

    private fun verifyFileSignature(
        publication: PublicationArtifacts,
        file: File,
        kind: String,
        signatures: Map<File, File>,
        required: Boolean,
        errors: MutableList<String>,
        tally: Tally
    ) {
        // Every publication writes its POM to build/publications/<name>/pom-default.xml, so the
        // file name alone cannot tell two publications apart in a report. Name both.
        val subject = "$kind of publication '${publication.name}' at '${file.path}'"
        tally.inspected++

        if (!file.exists()) {
            // A safety net rather than an expected path: the task depends on the publication's
            // artifacts, so they should already exist by the time this runs.
            errors.add("$subject has not been built, so its signature could not be checked.")
            return
        }

        val signatureFile = signatures[file.absoluteFile]

        if (signatureFile == null || !signatureFile.exists()) {
            val problem = if (signatureFile == null) {
                // Lead with the cause that is actually common. `sign(configurations["archives"])`
                // covers the jar but not the POM or module.json, and those two land here with
                // signing still required. The stale-output case -- a build/ directory left over
                // from when this publication still carried module metadata -- is second, and
                // deliberately so: './gradlew clean' would delete a POM this build just generated.
                "No PGP signature is registered for $subject. Check that the project's " +
                    "'signing { sign(...) }' covers this file -- 'sign(publishing.publications)' " +
                    "covers a publication's artifacts, its POM and its module metadata. If instead " +
                    "the file is left over from an earlier publication layout and nothing publishes " +
                    "it any more, './gradlew clean' removes it."
            } else {
                "The PGP signature for $subject was never produced (expected it at '${signatureFile.path}')."
            }

            if (required) {
                errors.add("$problem Maven Central requires every published file to be signed.")
            } else {
                // `setRequired(false)` opted out of signing everything, so an absent signature is
                // the user's choice. A *broken* one below still fails: that is never intentional.
                logger.warn("$problem Signing is not required, so this is not an error.")
                tally.unsigned++
            }
            return
        }

        val result = PgpSignatureVerifier.verify(file, signatureFile)
        if (result.isOk) {
            logger.info("PGP signature verified for $kind ${file.name}. ${result.detail}")
            tally.verified++
        } else {
            // A broken signature fails regardless of `required`: nobody opts into those.
            errors.add("PGP signature verification FAILED for $subject: ${result.detail}")
        }
    }

    /** How much of the signature check actually happened, so the task can report it truthfully. */
    private enum class SignatureCheck { VERIFIED, PARTIAL, SKIPPED }

    /** The running count behind that verdict. */
    private class Tally {
        /** Files this check looked at, whatever the verdict. */
        var inspected: Int = 0

        /** Files whose signature was found and parsed. */
        var verified: Int = 0

        /** Files with no signature, tolerated because signing is not required. */
        var unsigned: Int = 0
    }
}
