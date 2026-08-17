package io.github.yonggoose.organizationdefaults

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.Signature
import org.gradle.plugins.signing.SigningExtension
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap

/**
 * A Gradle plugin that adds an artifact verification task to the project.
 * Validates the metadata of artifacts to be published according to Maven Central requirements
 * and verifies local PGP signatures using Bouncy Castle.
 *
 * The rules themselves live in [MavenCentralMetadataValidator] and [PgpSignatureVerifier], and the
 * report lives in [CheckProjectArtifactTask]. This class only decides, at configuration time, what
 * that task will be given.
 *
 * Everything is handed over through `Provider`s. They are resolved once configuration is finished,
 * which puts them after every `afterEvaluate` in the build — including the one
 * [OrganizationDefaultsProjectPlugin] uses to write `mergedDefaults` — so the two plugins can be
 * applied in either order. It is also what keeps `Project` out of the task: with the configuration
 * cache on, a task action holding one is refused outright.
 */
class ArtifactCheckPluginForProject : Plugin<Project> {

    override fun apply(project: Project) {
        project.tasks.register(TASK_NAME, CheckProjectArtifactTask::class.java) {
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
            // any report could be printed.
            //
            // Note that `signing { setRequired(false) }` only rescues a keyless machine when no
            // signatory is configured either: Gradle's Sign carries
            // `onlyIf { isRequired || signatory != null }`, so with `useGpgCmd()` it stays in the
            // graph and fails there. `-x signMavenPublication` is the way out in that case; the
            // docs spell both out.
            dependsOn(project.tasks.withType(Sign::class.java))
            dependsOn(project.tasks.withType(GenerateMavenPom::class.java))
            dependsOn(project.tasks.withType(GenerateModuleMetadata::class.java))

            // And on the artifacts themselves: signing them does not, on its own, guarantee they
            // are in the graph, and an artifact that was never built cannot be checked.
            dependsOn(
                project.provider {
                    publishingOf(project)
                        ?.publications
                        ?.withType(MavenPublication::class.java)
                        ?.flatMap { it.artifacts }
                        ?: emptyList()
                }
            )

            projectPath.set(project.path)
            mergedDefaults.set(project.provider { resolveMergedDefaults(project) })
            publishingApplied.set(project.provider { publishingOf(project) != null })
            signingApplied.set(project.provider { signingOf(project) != null })
            signingRequired.set(project.provider { signingOf(project)?.isRequired ?: false })
            publicationCount.set(project.provider { publishingOf(project)?.publications?.size ?: 0 })
            publications.set(project.provider { publicationArtifacts(project) })
            signaturesBySignedFile.set(project.provider { collectSignatures(project) })
        }
    }

    private fun publishingOf(project: Project): PublishingExtension? =
        project.extensions.findByType(PublishingExtension::class.java)

    private fun signingOf(project: Project): SigningExtension? =
        project.extensions.findByType(SigningExtension::class.java)

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

    private fun publicationArtifacts(project: Project): List<PublicationArtifacts> {
        val publishing = publishingOf(project) ?: return emptyList()

        return publishing.publications.withType(MavenPublication::class.java).map { publication ->
            PublicationArtifacts(
                name = publication.name,
                artifacts = publication.artifacts.map { it.file },
                pomTaskName = pomTaskNameFor(publication),
                pomFile = pomFileOf(project, publication),
                moduleMetadataFile = moduleMetadataFileOf(project, publication)
            )
        }
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
     *
     * Signatures whose `Sign` task is not part of this build are left out. The `Signature` objects
     * exist whether or not anything signed, so taking them all would let a stale `.asc` in a dirty
     * `build/` stand in for a signature this build never produced — exactly what
     * `-x signMavenPublication`, and `setRequired(false)` on a keyless machine, leave behind.
     */
    private fun collectSignatures(project: Project): Map<File, File> {
        val signing = signingOf(project) ?: return emptyMap()
        val bySignedFile = LinkedHashMap<File, File>()

        fun record(signature: Signature) {
            val signed = signature.toSign ?: return
            bySignedFile[signed.absoluteFile] = signature.file
        }

        // Identity, not equality: two signatures over different files can compare equal by name,
        // and all this set has to answer is "did some Sign task already account for this object".
        val ownedByTask: MutableSet<Signature> = Collections.newSetFromMap(IdentityHashMap())
        project.tasks.withType(Sign::class.java).forEach { task ->
            ownedByTask.addAll(task.signatures)
            if (task.willSign()) {
                task.signatures.forEach(::record)
            }
        }

        // Whatever is left in the configuration has no Sign task to judge it by; take it as is.
        signing.configuration.artifacts
            .filterIsInstance<Signature>()
            .filterNot { it in ownedByTask }
            .forEach(::record)

        return bySignedFile
    }

    /**
     * Whether [this] task is part of this build at all.
     *
     * Asked at configuration time, where the answer has to come from the task graph rather than
     * from an outcome: a task's `state` does not exist yet, and reading one at execution time is
     * what the configuration cache forbids. The two ways a task drops out before it can produce
     * anything are both visible here — `enabled = false`, and `-x` on the command line.
     *
     * `-x` matching mirrors Gradle only as far as full names and paths. Gradle also accepts
     * camel-case abbreviations (`-x sMP`), and those are not resolved here: such a build is
     * treated as though the task still runs, which errs towards checking a signature rather than
     * towards silently passing one.
     */
    private fun Task.willRunInThisBuild(): Boolean {
        if (!enabled) {
            return false
        }
        val excluded = project.gradle.startParameter.excludedTaskNames
        return excluded.none { it == name || it == path }
    }

    /**
     * Whether [this] `Sign` task will actually produce signatures.
     *
     * Adds Gradle's own `onlyIf { isRequired || signatory != null }` to [willRunInThisBuild], which
     * is the third way signatures fail to appear: `setRequired(false)` on a machine with no
     * signatory configured, the documented way to get a metadata-only run.
     */
    private fun Sign.willSign(): Boolean {
        if (!willRunInThisBuild()) {
            return false
        }
        if (isRequired) {
            return true
        }
        // Only reached when signing is not required, which is also the case where reading the
        // signatory is most likely to blow up -- PgpSignatoryProvider parses the key ring on
        // access. A signatory that cannot be constructed is one that cannot sign, so the failure
        // is the answer rather than something to propagate out of configuration.
        return runCatching { signatory }.getOrNull() != null
    }

    /**
     * Where [publication]'s Gradle Module Metadata will be written, or `null` when this build does
     * not generate any.
     *
     * Decided by whether the producing task is in the build, not by the file: keying off existence
     * alone would demand a signature for a stale `module.json` left in `build/` by an earlier
     * publication layout. Existence is checked later, by the task, since nothing has been written
     * yet at the point this runs.
     */
    private fun moduleMetadataFileOf(project: Project, publication: MavenPublication): File? =
        project.tasks.withType(GenerateModuleMetadata::class.java)
            .findByName("generateMetadataFileFor${capitalize(publication.name)}Publication")
            ?.takeIf { it.willRunInThisBuild() }
            ?.outputFile
            ?.orNull
            ?.asFile

    /**
     * Where [publication]'s POM will be written, or `null` when this build does not generate one.
     *
     * Same reasoning as [moduleMetadataFileOf]: a `GenerateMavenPom` that was disabled or excluded
     * leaves its `destination` pointing at whatever an earlier build wrote there, and validating a
     * stale POM — or demanding a signature for one this build will not publish — is worse than
     * reporting that none was generated. There is no fallback to the conventional
     * `build/publications/<name>/pom-default.xml` path either, for the same reason.
     */
    private fun pomFileOf(project: Project, publication: MavenPublication): File? =
        project.tasks.withType(GenerateMavenPom::class.java)
            .findByName(pomTaskNameFor(publication))
            ?.takeIf { it.willRunInThisBuild() }
            ?.destination

    private fun pomTaskNameFor(publication: MavenPublication): String =
        "$GENERATE_POM_TASK_PREFIX${capitalize(publication.name)}Publication"

    private fun capitalize(value: String): String = value.replaceFirstChar { it.uppercaseChar() }

    companion object {
        private const val TASK_NAME = "checkProjectArtifact"
        private const val GENERATE_POM_TASK_PREFIX = "generatePomFileFor"
    }
}
