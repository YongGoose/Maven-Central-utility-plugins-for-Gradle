package io.github.yonggoose.organizationdefaults

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.Signature
import org.gradle.plugins.signing.SigningExtension
import java.io.File
import javax.inject.Inject

/**
 * A Gradle plugin that adds an artifact verification task to the project.
 * Validates the metadata of artifacts to be published according to Maven Central requirements
 * and verifies local PGP signatures using Bouncy Castle.
 *
 * The rules themselves live in [MavenCentralMetadataValidator] and [PgpSignatureVerifier], and the
 * report lives in [CheckProjectArtifactTask]. This class only decides, at configuration time, what
 * that task will be given.
 *
 * Everything is handed over through `Provider`s, which resolve once configuration is finished.
 * That is what keeps `Project` out of the task: with the configuration cache on, a task action
 * holding one is refused outright. What genuinely cannot be known before execution — whether the
 * task behind a file actually produced it — is left to [ProducedOutputsService].
 */
class ArtifactCheckPluginForProject @Inject constructor(
    private val listenerRegistry: BuildEventsListenerRegistry
) : Plugin<Project> {

    override fun apply(project: Project) {
        val producedOutputsService = project.gradle.sharedServices.registerIfAbsent(
            ProducedOutputsService.NAME,
            ProducedOutputsService::class.java
        ) {}
        // Subscribing is what makes the service receive anything; registering it alone does not.
        listenerRegistry.onTaskCompletion(producedOutputsService)

        project.tasks.register(TASK_NAME, CheckProjectArtifactTask::class.java) {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description =
                "Verifies that all artifacts staged for publishing are signed and meet Maven Central requirements."

            // The signatures, the POM and the module metadata only exist on disk once their
            // producing tasks have run. Depending on them is also what puts their completion
            // events ahead of this task's own execution.
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

            usesService(producedOutputsService)
            producedOutputs.set(producedOutputsService)

            projectPath.set(project.path)
            mergedDefaults.set(project.provider { resolveMergedDefaults(project) })
            publishingApplied.set(project.provider { publishingOf(project) != null })
            signingApplied.set(project.provider { signingOf(project) != null })
            signingRequired.set(project.provider { signingOf(project)?.isRequired ?: false })
            publicationCount.set(project.provider { publishingOf(project)?.publications?.size ?: 0 })
            publications.set(project.provider { publicationArtifacts(project) })
            signaturePlans.set(project.provider { signaturePlans(project) })
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

    /**
     * The files of every Maven publication, paired with the task that has to have produced each.
     *
     * Nothing is filtered here. Whether the POM and the module metadata belong to this build is a
     * question about task outcomes, which only exist during execution — the task asks
     * [ProducedOutputsService] and checks the files then.
     */
    private fun publicationArtifacts(project: Project): List<PublicationArtifacts> {
        val publishing = publishingOf(project) ?: return emptyList()

        return publishing.publications.withType(MavenPublication::class.java).map { publication ->
            val pomTask = project.tasks.withType(GenerateMavenPom::class.java)
                .findByName(pomTaskNameFor(publication))
            val moduleTask = project.tasks.withType(GenerateModuleMetadata::class.java)
                .findByName("generateMetadataFileFor${capitalize(publication.name)}Publication")

            PublicationArtifacts(
                name = publication.name,
                artifacts = publication.artifacts.map { it.file },
                pomTaskName = pomTaskNameFor(publication),
                pomTaskPath = pomTask?.path,
                pomFile = pomTask?.destination,
                moduleTaskPath = moduleTask?.path,
                moduleMetadataFile = moduleTask?.outputFile?.orNull?.asFile
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
     */
    private fun signaturePlans(project: Project): List<SignaturePlan> {
        val signing = signingOf(project) ?: return emptyList()
        val plans = LinkedHashMap<File, SignaturePlan>()

        fun record(signature: Signature, producedBy: String?) {
            val signed = signature.toSign ?: return
            // Kept despite the compiler calling the elvis redundant: `Signature.getFile()` is
            // derived from `toSign` and the signature type, and a Signature that cannot name its
            // own file has nothing to contribute. Without this the map below takes a null and the
            // build dies during configuration, before any report is printed.
            val signatureFile: File = signature.file ?: return
            plans[signed.absoluteFile] = SignaturePlan(signed.absoluteFile, signatureFile, producedBy)
        }

        project.tasks.withType(Sign::class.java).forEach { task ->
            task.signatures.forEach { record(it, task.path) }
        }

        // Whatever is left in the configuration has no Sign task to judge it by; take it as is.
        // `containsKey` rather than an identity set over the Signature objects: two signatures can
        // compare equal by name, and the question here is only whether some Sign task already
        // claimed this signed file.
        signing.configuration.artifacts
            .filterIsInstance<Signature>()
            .forEach { signature ->
                val signed = signature.toSign?.absoluteFile
                if (signed != null && !plans.containsKey(signed)) {
                    record(signature, null)
                }
            }

        return plans.values.toList()
    }

    private fun pomTaskNameFor(publication: MavenPublication): String =
        "$GENERATE_POM_TASK_PREFIX${capitalize(publication.name)}Publication"

    private fun capitalize(value: String): String = value.replaceFirstChar { it.uppercaseChar() }

    companion object {
        private const val TASK_NAME = "checkProjectArtifact"
        private const val GENERATE_POM_TASK_PREFIX = "generatePomFileFor"
    }
}
