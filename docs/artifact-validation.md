# Artifact Signing and Validation

All artifacts published to Maven Central require a PGP signature, and the metadata must meet Maven Central's requirements. This plugin verifies that these requirements are met before publication.

### Usage

```kotlin
plugins {
    id("io.github.yonggoose.maven.central.utility.plugin.check") version "0.1.6"
}

// Run the validation task
./gradlew checkProjectArtifact
```
## Metadata Validation
The plugin validates the following fields:
- `groupId` - Checks if it follows the reverse domain name format (e.g., `io.github.yonggoose`).
- `artifactId` - Ensures it is not empty.
- `version` - Ensures it is not empty and is not a snapshot version (does not end with `-SNAPSHOT`).

```Kotlin
private fun validateMetadata(pom: OrganizationDefaults, errors: MutableList<String>) {
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
}
```

## PGP Signature Validation
All artifacts published to Maven Central must be signed with PGP. The plugin checks the following:

1. Ensures an accompanying `.asc` signature file exists for every artifact.
2. Confirms the POM file itself is signed.
3. Verifies that the signature file format is correct (using the BouncyCastle library).

```Kotlin
private fun validatePgpSignatures(project: Project, errors: MutableList<String>) {
    val publishing = project.extensions.findByType(PublishingExtension::class.java)
    val signing = project.extensions.findByType(SigningExtension::class.java)

    // Check if required plugins are applied
    if (publishing == null || signing == null) {
        errors.add("Required plugins not found")
        return
    }

    // Check signing configuration
    if (!signing.isRequired) {
        project.logger.warn("Signing is not required. Skipping verification.")
        return
    }

    // Verify artifacts and signatures
    publishing.publications.forEach { publication ->
        if (publication is MavenPublication) {
            validateMavenPublicationSignatures(project, publication, signing.configuration.artifacts, errors)
        }
    }
}
```

## Detailed PGP Signature Verification
It uses the BouncyCastle library to verify the structural validity of the PGP signature:

```Kotlin
private fun verifyPgpSignaturePresenceWithBouncyCastle(
    artifactFile: File,
    signatureFile: File,
    project: Project
): Boolean {
    return try {
        val signatureInputStream = PGPUtil.getDecoderStream(FileInputStream(signatureFile))
        val pgpFactory = PGPObjectFactory(signatureInputStream, JcaKeyFingerprintCalculator())

        val obj = pgpFactory.nextObject()
        if (obj !is PGPSignatureList) {
            project.logger.error("Invalid signature file format")
            return false
        }

        if (obj.size() == 0) {
            project.logger.error("No signatures found in signature file")
            return false
        }

        val signature = obj[0]
        project.logger.info("Found signature with key ID: ${String.format("0x%X", signature.keyID)}")
        true
    } catch (e: Exception) {
        project.logger.error("Error verifying signature: ${e.message}")
        false
    }
}
```

## Integrated Usage Example
This plugin can be used alongside the Maven Publish plugin to validate artifacts before publishing:

```Kotlin
plugins {
    id("io.github.yonggoose.maven.central.utility.plugin.project") version "0.1.6"
    id("io.github.yonggoose.maven.central.utility.plugin.check") version "0.1.6"
    id("com.vanniktech.maven.publish") version "0.29.0"
    signing
}

rootProjectPom {
    groupId = "io.github.yonggoose"
    artifactId = "my-library"
    version = "1.0.0"
    // Other required metadata...
}

signing {
    // PGP signing configuration
    useGpgCmd()
    sign(publishing.publications)
}

// Run validation before publishing
tasks.named("publishToMavenLocal") {
    dependsOn("checkProjectArtifact")
}
```

With this setup, you can ensure all requirements are met before publishing to Maven Central, allowing you to catch potential issues early.