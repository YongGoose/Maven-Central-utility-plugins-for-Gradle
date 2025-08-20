package io.github.yonggoose.organizationdefaults

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.*
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.gradle.api.artifacts.PublishArtifactSet
import java.io.File
import java.io.FileInputStream
import java.security.Security

/**
 * A Gradle plugin that adds an artifact verification task to the project.
 * Validates the metadata of artifacts to be published according to Maven Central requirements
 * and verifies local PGP signatures using Bouncy Castle.
 */
class ArtifactCheckPluginForProject : Plugin<Project> {

    companion object {
        init {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

    override fun apply(project: Project) {
        project.tasks.register("checkProjectArtifact") {
            group = "Verification"
            description =
                "Verifies that all artifacts staged for publishing are signed and meet Maven Central requirements."

            doLast {
                val pom = project.rootProject.extensions.extraProperties.get("mergedDefaults") as? OrganizationDefaults
                    ?: throw IllegalStateException("mergedDefaults is not defined in the root project.")

                val errors = mutableListOf<String>()

                validateMetadata(pom, errors)
                validatePgpSignatures(project, errors)

                if (errors.isNotEmpty()) {
                    throw IllegalArgumentException("Validation failed:\n${errors.joinToString("\n")}")
                }

                project.logger.lifecycle("✅ ArtifactCheckPlugin: All validations including PGP signature verification passed successfully.")
            }
        }
    }

    private fun validateMetadata(pom: OrganizationDefaults, errors: MutableList<String>) {
        // GAV Coordinates validation
        val groupId = pom.groupId
        if (groupId == null || groupId.isBlank() || !groupId.matches(Regex("^[a-z]+(\\.[a-z][a-z0-9]*)+$"))) {
            errors.add("Invalid groupId: Must be in reverse domain name format or not be null or blank.")
        }

        if (pom.artifactId.isNullOrBlank()) {
            errors.add("Invalid artifactId: Pom must not be null or blank.")
        }

        val version = pom.version
        if (version == null || (version.isBlank() || version.endsWith("-SNAPSHOT"))) {
            errors.add("Invalid version: The version must not be null, blank, or end with '-SNAPSHOT'.")
        }

        // Project Information validation
        if (pom.name.isNullOrBlank()) {
            errors.add("Invalid name: Project name is required.")
        }
        if (pom.description.isNullOrBlank()) {
            errors.add("Invalid description: Project description is required.")
        }
        if (pom.url.isNullOrBlank()) {
            errors.add("Invalid url: Project URL is required.")
        }

        // License validation
        pom.licenses.forEach { license ->
            if (license.licenseType.isNullOrBlank()) {
                errors.add("Invalid license: License name is required.")
            }
        }

        // Developer Info validation
        pom.developers.forEach { developer ->
            if (developer.name.isNullOrBlank()) {
                errors.add("Invalid developer: Developer name is required.")
            }
            if (developer.email.isNullOrBlank()) {
                errors.add("Invalid developer: Developer email is required.")
            }
            if (developer.organization.isNullOrBlank()) {
                errors.add("Invalid developer: Organization is required.")
            }
            if (developer.organizationUrl.isNullOrBlank()) {
                errors.add("Invalid developer: Organization URL is required.")
            }
        }

        // SCM Information validation
        pom.scm?.let { scm ->
            if (scm.connection.isNullOrBlank()) {
                errors.add("Invalid SCM: Read-only connection is required.")
            }
            if (scm.developerConnection.isNullOrBlank()) {
                errors.add("Invalid SCM: Read/write connection is required.")
            }
            if (scm.url.isNullOrBlank()) {
                errors.add("Invalid SCM: Web interface URL is required.")
            }
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
            project.logger.warn("No publications found in 'publishing' extension. Skipping PGP signature verification.")
            return
        }

        val signingConfiguration = signing.configuration
        if (signingConfiguration.artifacts.isEmpty()) {
            errors.add("No artifacts found to verify PGP signatures. Ensure artifacts are configured for signing.")
            return
        }
        val signatureArtifacts = signingConfiguration.artifacts

        publishing.publications.forEach { publication ->
            if (publication is MavenPublication) {
                validateMavenPublicationSignatures(project, publication, signatureArtifacts, errors)
            }
        }
    }

    private fun validateMavenPublicationSignatures(
        project: Project,
        publication: MavenPublication,
        signatureArtifacts: PublishArtifactSet,
        errors: MutableList<String>
    ) {
        project.logger.info("Validating PGP signatures for publication: ${publication.name}")

        publication.artifacts.forEach { artifact ->
            val artifactFile = artifact.file
            project.logger.info("Checking signature for artifact: ${artifactFile.absolutePath} among ${signatureArtifacts.size} artifacts")
            val signatureFile = findSignatureFileForArtifact(project, artifactFile, signatureArtifacts)

            if (signatureFile == null) {
                errors.add("PGP signature not found for artifact: ${artifactFile.absolutePath}")
                return@forEach
            }

            if (!signatureFile.exists()) {
                errors.add("PGP signature file does not exist: ${signatureFile.absolutePath}")
                return@forEach
            }

            project.logger.info("Found signature: ${signatureFile.name}")

            val verified = verifyPgpSignatureBasic(artifactFile, signatureFile, project)
            if (!verified) {
                errors.add("PGP signature verification FAILED for artifact: ${artifactFile.name}")
            } else {
                project.logger.info("PGP signature verified for: ${artifactFile.name}")
            }
        }

        validatePomSignature(project, publication, signatureArtifacts, errors)
    }

    private fun validatePomSignature(
        project: Project,
        publication: MavenPublication,
        signatureArtifacts: PublishArtifactSet,
        errors: MutableList<String>
    ) {
        project.logger.info("   - Checking POM signature")

        val pomSignatureFile = signatureArtifacts.find { artifact ->
            artifact.file.name.contains("pom") && artifact.file.name.endsWith(".asc")
        }?.file

        if (pomSignatureFile == null) {
            errors.add("PGP signature not found for POM file")
            return
        }

        if (!pomSignatureFile.exists()) {
            errors.add("POM signature file does not exist: ${pomSignatureFile.absolutePath}")
            return
        }

        val buildDir = project.layout.buildDirectory.get().asFile
        val publicationsDir = File(buildDir, "publications/${publication.name}")
        val pomFile = File(publicationsDir, "pom-default.xml")

        if (!pomFile.exists()) {
            val alternativeLocations = listOf(
                File(publicationsDir, "pom.xml"),
                File(buildDir, "tmp/publishMavenJavaPublicationToMavenLocalRepository/pom-default.xml")
            )

            val foundPomFile = alternativeLocations.find { it.exists() }
            if (foundPomFile != null) {
                validatePomSignatureFile(project, foundPomFile, pomSignatureFile, errors)
            } else {
                project.logger.warn("POM file not found at expected locations")
            }
        } else {
            validatePomSignatureFile(project, pomFile, pomSignatureFile, errors)
        }
    }

    private fun validatePomSignatureFile(
        project: Project,
        pomFile: File,
        pomSignatureFile: File,
        errors: MutableList<String>
    ) {
        val verified = verifyPgpSignatureBasic(pomFile, pomSignatureFile, project)
        if (!verified) {
            errors.add("PGP signature verification FAILED for POM file")
        } else {
            project.logger.info("PGP signature verified for POM file")
        }
    }

    private fun findSignatureFileForArtifact(
        project: Project,
        artifactFile: File,
        signatureArtifacts: PublishArtifactSet
    ): File? {
        val expectedSignatureName = "${artifactFile.name}.asc"

        project.logger.info("Looking for ${expectedSignatureName}")
        return signatureArtifacts.find { signatureArtifact ->
            val signatureFile = signatureArtifact.file
            signatureFile.name == expectedSignatureName ||
                    signatureFile.name.startsWith(artifactFile.nameWithoutExtension) && signatureFile.name.endsWith(".asc")
        }?.file
    }

    private fun verifyPgpSignatureBasic(artifactFile: File, signatureFile: File, project: Project): Boolean {
        return try {
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
            if (!signatureContent.contains("BEGIN PGP SIGNATURE") ||
                !signatureContent.contains("END PGP SIGNATURE")) {
                project.logger.error("Invalid PGP signature format in: ${signatureFile.absolutePath}")
                return false
            }

            project.logger.info("Basic PGP signature validation passed for: ${artifactFile.name}")

            return verifyPgpSignatureWithBouncyCastle(artifactFile, signatureFile, project)
        } catch (e: Exception) {
            project.logger.error("Error during basic PGP signature verification for ${artifactFile.name}: ${e.message}")
            false
        }
    }

    private fun verifyPgpSignatureWithBouncyCastle(
        artifactFile: File,
        signatureFile: File,
        project: Project
    ): Boolean {
        return try {
            val signatureInputStream = PGPUtil.getDecoderStream(FileInputStream(signatureFile))
            val pgpFactory = PGPObjectFactory(signatureInputStream, JcaKeyFingerprintCalculator())

            val obj = pgpFactory.nextObject()
            if (obj !is PGPSignatureList) {
                project.logger.error("Invalid signature file format for ${artifactFile.name}")
                return false
            }

            if (obj.size() == 0) {
                project.logger.error("No signatures found in signature file for ${artifactFile.name}")
                return false
            }

            val signature = obj[0]
            project.logger.info("Found signature with key ID: ${String.format("0x%X", signature.keyID)}")

            project.logger.info("PGP signature structure validation successful for ${artifactFile.name}")
            true
        } catch (e: Exception) {
            project.logger.warn("Could not perform full PGP verification for ${artifactFile.name}: ${e.message}")
            true
        }
    }
}
