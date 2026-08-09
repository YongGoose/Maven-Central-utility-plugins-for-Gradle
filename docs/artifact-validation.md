# Artifact Signing and Validation

All artifacts published to Maven Central require a PGP signature, and the metadata must meet Maven Central's requirements. This plugin verifies that these requirements are met before publication.

### Usage

```kotlin
plugins {
    id("io.github.yonggoose.maven.central.utility.plugin.check") version "0.1.7"
}
```

```bash
# Run the validation task
./gradlew checkProjectArtifact
```

The task depends on the project's `Sign` and `GenerateMavenPom` tasks, so the signatures and the
POM it inspects are always produced first — there is no need to chain it after `publish` manually.

In a multi-module build the task validates **the metadata of the project it runs in**, i.e. the
result of merging `rootProjectPom` with that module's `projectPom`. Running
`./gradlew :sub:checkProjectArtifact` therefore checks `:sub`'s effective POM, overrides included.

## Metadata Validation

The plugin validates every field Maven Central requires
([publishing requirements](https://central.sonatype.org/publish/requirements/)):

| Field | Rule |
|---|---|
| `groupId` | Non-blank, dotted Maven coordinate (`io.github.yonggoose`). Digits and hyphens are allowed, so `log4j.log4j` and `io.github.my-org` are accepted. |
| `artifactId` | Non-blank Maven coordinate. |
| `version` | Non-blank and not a snapshot (must not end with `-SNAPSHOT`). |
| `name` | Non-blank. |
| `description` | Non-blank. |
| `url` | Non-blank. |
| `licenses` | At least one entry, each with a `name`. |
| `developers` | At least one entry, each with an `id` or a `name`. |
| `scm` | Present, with a non-blank `url`. |

All violations are collected and reported together, so one run tells you everything that is wrong:

```
Validation failed:
Missing description: Maven Central requires a project description.
Missing licenses: Maven Central requires at least one license.
Missing scm: Maven Central requires source control information.
```

## PGP Signature Validation

Maven Central requires every published file to be signed. The plugin checks that:

1. A `.asc` signature exists for every artifact in each `MavenPublication`.
2. The generated POM is signed too.
3. Each signature file parses as a well-formed detached PGP signature (using BouncyCastle).

Signature files are matched to their artifact **by exact name** — `foo-1.0.jar` is only ever paired
with `foo-1.0.jar.asc`. A prefix match would let `foo-1.0-sources.jar.asc` stand in for the main
jar, which would defeat the point of the check.

Anything the plugin cannot read is reported as a failure rather than passed over:

```kotlin
private fun readSignatureList(project: Project, artifactFile: File, signatureFile: File): PGPSignatureList? {
    return try {
        FileInputStream(signatureFile).use { input ->
            val decoded = PGPUtil.getDecoderStream(input)
            val pgpFactory = PGPObjectFactory(decoded, JcaKeyFingerprintCalculator())

            val obj = pgpFactory.nextObject()
            if (obj !is PGPSignatureList) { /* report and return null */ }
            if (obj.size() == 0) { /* report and return null */ }
            obj
        }
    } catch (e: Exception) {
        // Fail closed: an unreadable signature is not a verified signature.
        project.logger.error("Could not read the PGP signature for ${artifactFile.name}: ${e.message}", e)
        null
    }
}
```

### Current limitation

The signature check validates **structure**, not cryptographic validity: the plugin does not yet
verify a signature against a public key, so it cannot detect a well-formed signature produced over
different content. Full verification is tracked in
[#22](https://github.com/YongGoose/Maven-Central-utility-plugins-for-Gradle/issues/22).

Note also that `signing { setRequired(false) }` skips signature verification entirely, with a
warning — only the metadata checks run in that configuration.

## Integrated Usage Example

This plugin can be used alongside the Maven Publish plugin to validate artifacts before publishing:

```kotlin
plugins {
    id("io.github.yonggoose.maven.central.utility.plugin.project") version "0.1.7"
    id("io.github.yonggoose.maven.central.utility.plugin.check") version "0.1.7"
    id("com.vanniktech.maven.publish") version "0.34.0"
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
