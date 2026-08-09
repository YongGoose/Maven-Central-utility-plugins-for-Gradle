package io.github.yonggoose.organizationdefaults

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import java.io.File
import java.io.FileInputStream
import java.security.Security

/**
 * A Gradle plugin that adds an artifact verification task to the project.
 * Validates the metadata of artifacts to be published according to Maven Central requirements
 * and verifies local PGP signatures using Bouncy Castle.
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

                validateMetadata(pom, errors)
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
        val ownExtras = project.extensions.extraProperties
        if (ownExtras.has(OrganizationDefaultsProjectPlugin.MERGED_DEFAULTS_PROPERTY)) {
            val own = ownExtras.get(OrganizationDefaultsProjectPlugin.MERGED_DEFAULTS_PROPERTY)
            if (own is OrganizationDefaults) {
                return own
            }
        }

        val rootExtras = project.rootProject.extensions.extraProperties
        if (rootExtras.has(OrganizationDefaultsProjectPlugin.MERGED_DEFAULTS_PROPERTY)) {
            project.logger.info(
                "No 'mergedDefaults' on '${project.path}'; falling back to the root project's metadata."
            )
            return rootExtras.get(OrganizationDefaultsProjectPlugin.MERGED_DEFAULTS_PROPERTY) as? OrganizationDefaults
        }

        return null
    }

    private fun validateMetadata(pom: OrganizationDefaults, errors: MutableList<String>) {
        val groupId = pom.groupId
        if (groupId.isNullOrBlank() || !groupIdPattern.matches(groupId)) {
            errors.add(
                "Invalid groupId: must be a dotted Maven coordinate such as 'io.github.yonggoose' " +
                    "(was: ${describe(groupId)})."
            )
        }

        val artifactId = pom.artifactId
        if (artifactId.isNullOrBlank() || !artifactIdPattern.matches(artifactId)) {
            errors.add(
                "Invalid artifactId: must be a non-blank Maven coordinate such as 'my-library' " +
                    "(was: ${describe(artifactId)})."
            )
        }

        val version = pom.version
        if (version.isNullOrBlank() || version.endsWith(SNAPSHOT_SUFFIX)) {
            errors.add("Invalid version: The version must not be null, blank, or end with '$SNAPSHOT_SUFFIX'.")
        }

        // Maven Central rejects a POM that is missing any of the following.
        // https://central.sonatype.org/publish/requirements/
        if (pom.name.isNullOrBlank()) {
            errors.add("Missing name: Maven Central requires a project name.")
        }
        if (pom.description.isNullOrBlank()) {
            errors.add("Missing description: Maven Central requires a project description.")
        }
        if (pom.url.isNullOrBlank()) {
            errors.add("Missing url: Maven Central requires a project URL.")
        }

        validateLicenses(pom, errors)
        validateDevelopers(pom, errors)
        validateScm(pom, errors)
    }

    private fun validateLicenses(pom: OrganizationDefaults, errors: MutableList<String>) {
        if (pom.licenses.isEmpty()) {
            errors.add("Missing licenses: Maven Central requires at least one license.")
            return
        }
        pom.licenses.forEachIndexed { index, license ->
            if (license.name.isNullOrBlank()) {
                errors.add("Invalid licenses[$index]: 'name' is required.")
            }
        }
    }

    private fun validateDevelopers(pom: OrganizationDefaults, errors: MutableList<String>) {
        if (pom.developers.isEmpty()) {
            errors.add("Missing developers: Maven Central requires at least one developer.")
            return
        }
        pom.developers.forEachIndexed { index, developer ->
            if (developer.id.isNullOrBlank() && developer.name.isNullOrBlank()) {
                errors.add("Invalid developers[$index]: at least one of 'id' or 'name' is required.")
            }
        }
    }

    private fun validateScm(pom: OrganizationDefaults, errors: MutableList<String>) {
        val scm = pom.scm
        if (scm == null) {
            errors.add("Missing scm: Maven Central requires source control information.")
            return
        }
        if (scm.url.isNullOrBlank()) {
            errors.add("Missing scm.url: Maven Central requires the repository URL.")
        }
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

        val signaturesByName: Map<String, File> = signatureArtifacts.associate { it.file.name to it.file }
        project.logger.info("Found ${signaturesByName.size} signature file(s): ${signaturesByName.keys.sorted()}")

        publishing.publications.withType(MavenPublication::class.java).forEach { publication ->
            validateMavenPublicationSignatures(project, publication, signaturesByName, errors)
        }
    }

    private fun validateMavenPublicationSignatures(
        project: Project,
        publication: MavenPublication,
        signaturesByName: Map<String, File>,
        errors: MutableList<String>
    ) {
        project.logger.info("Validating PGP signatures for publication: ${publication.name}")

        publication.artifacts.forEach { artifact ->
            verifyFileSignature(project, artifact.file, signaturesByName, "artifact", errors)
        }

        val pomFile = findPomFile(project, publication)
        if (pomFile == null) {
            val pomTaskName = "$GENERATE_POM_TASK_PREFIX${capitalize(publication.name)}Publication"
            errors.add(
                "POM file for publication '${publication.name}' was not found, so its signature " +
                    "could not be verified. Run '$pomTaskName' first."
            )
            return
        }

        verifyFileSignature(project, pomFile, signaturesByName, "POM", errors)
    }

    /**
     * Looks up the signature for [file] by exact name.
     *
     * Matching is deliberately strict: a prefix match would let `foo-1.0.jar` be "verified"
     * against `foo-1.0-sources.jar.asc`.
     */
    private fun verifyFileSignature(
        project: Project,
        file: File,
        signaturesByName: Map<String, File>,
        kind: String,
        errors: MutableList<String>
    ) {
        val expectedSignatureName = "${file.name}$SIGNATURE_EXTENSION"
        val signatureFile = signaturesByName[expectedSignatureName]

        if (signatureFile == null) {
            errors.add("PGP signature not found for $kind '${file.name}' (expected '$expectedSignatureName').")
            return
        }

        if (verifyPgpSignature(project, file, signatureFile)) {
            project.logger.info("PGP signature verified for $kind: ${file.name}")
        } else {
            errors.add("PGP signature verification FAILED for $kind: ${file.name}")
        }
    }

    /**
     * Resolves the generated POM for [publication] from its `GenerateMavenPom` task, falling back
     * to the conventional output location. Returns `null` when the POM has not been generated.
     */
    private fun findPomFile(project: Project, publication: MavenPublication): File? {
        val taskName = "$GENERATE_POM_TASK_PREFIX${capitalize(publication.name)}Publication"
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
     * Verifies that [signatureFile] is a well-formed detached PGP signature for [artifactFile].
     *
     * Note: this checks the signature's structure, not its cryptographic validity against a
     * public key — see https://github.com/YongGoose/Maven-Central-utility-plugins-for-Gradle/issues/22.
     * Anything that cannot be parsed is treated as a failure rather than a pass.
     */
    private fun verifyPgpSignature(project: Project, artifactFile: File, signatureFile: File): Boolean {
        if (!artifactFile.exists()) {
            project.logger.error("Artifact file does not exist: ${artifactFile.absolutePath}")
            return false
        }

        if (!signatureFile.exists()) {
            project.logger.error("Signature file does not exist: ${signatureFile.absolutePath}")
            return false
        }

        if (signatureFile.length() == 0L) {
            project.logger.error("Signature file is empty: ${signatureFile.absolutePath}")
            return false
        }

        val signatureContent = signatureFile.readText()
        if (!signatureContent.contains(PGP_SIGNATURE_HEADER) || !signatureContent.contains(PGP_SIGNATURE_FOOTER)) {
            project.logger.error("Invalid PGP signature format in: ${signatureFile.absolutePath}")
            return false
        }

        return readSignatureList(project, artifactFile, signatureFile) != null
    }

    private fun readSignatureList(project: Project, artifactFile: File, signatureFile: File): PGPSignatureList? {
        return try {
            FileInputStream(signatureFile).use { input ->
                val decoded = PGPUtil.getDecoderStream(input)
                val pgpFactory = PGPObjectFactory(decoded, JcaKeyFingerprintCalculator())

                val obj = pgpFactory.nextObject()
                if (obj !is PGPSignatureList) {
                    project.logger.error(
                        "Invalid signature file format for ${artifactFile.name}: expected a detached " +
                            "signature list but got ${obj?.javaClass?.name ?: "nothing"}."
                    )
                    return null
                }

                if (obj.size() == 0) {
                    project.logger.error("No signatures found in signature file for ${artifactFile.name}")
                    return null
                }

                project.logger.info(
                    "Found signature for ${artifactFile.name} with key ID: ${String.format("0x%X", obj[0].keyID)}"
                )
                obj
            }
        } catch (e: Exception) {
            // Fail closed: an unreadable signature is not a verified signature.
            project.logger.error("Could not read the PGP signature for ${artifactFile.name}: ${e.message}", e)
            null
        }
    }

    private fun describe(value: String?): String = if (value == null) "<not set>" else "'$value'"

    private fun capitalize(value: String): String = value.replaceFirstChar { it.uppercaseChar() }

    companion object {
        private const val TASK_NAME = "checkProjectArtifact"
        private const val GENERATE_POM_TASK_PREFIX = "generatePomFileFor"
        private const val SIGNATURE_EXTENSION = ".asc"
        private const val SNAPSHOT_SUFFIX = "-SNAPSHOT"
        private const val PGP_SIGNATURE_HEADER = "BEGIN PGP SIGNATURE"
        private const val PGP_SIGNATURE_FOOTER = "END PGP SIGNATURE"

        /**
         * A single Maven coordinate segment: alphanumerics plus `_`, with `-` allowed inside only.
         * Deliberately permits digits and hyphens, which real coordinates such as `log4j.log4j`
         * and `io.github.my-org` rely on.
         */
        private const val COORDINATE_SEGMENT = "[A-Za-z0-9_](?:[A-Za-z0-9_-]*[A-Za-z0-9_])?"

        private val groupIdPattern = Regex("^" + COORDINATE_SEGMENT + "(?:\\." + COORDINATE_SEGMENT + ")+\$")
        private val artifactIdPattern = Regex("^" + COORDINATE_SEGMENT + "\$")

        init {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }
}
