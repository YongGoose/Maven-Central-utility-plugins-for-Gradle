# Artifact Signing and Validation

All artifacts published to Maven Central require a PGP signature, and the metadata must meet Maven Central's requirements. This plugin verifies that these requirements are met before publication.

### Usage

The check plugin reads the metadata the **project** plugin produces and inspects the publications
`maven-publish` and `signing` set up, so all four belong in the same build:

```kotlin
plugins {
    `maven-publish`
    signing
    id("io.github.yonggoose.maven.central.utility.plugin.project") version "0.1.7"
    id("io.github.yonggoose.maven.central.utility.plugin.check") version "0.1.7"
}
```

Applying the check plugin on its own fails with
`No merged POM metadata found for ':' …` — there is nothing for it to validate, and it will not
silently fall back to another project's metadata.

```bash
# Run the validation task
./gradlew checkProjectArtifact
```

The task depends on the tasks that produce what it inspects — the publication's artifacts, its
`GenerateMavenPom` and `GenerateModuleMetadata` tasks, and its `Sign` tasks — so there is no need
to chain it after `publish` manually. On a machine without a usable signing key that means signing
fails first; see [Current limitations](#current-limitations).

In a multi-module build the task validates **the metadata of the project it runs in**, i.e. the
result of merging `rootProjectPom` with that module's `projectPom`. Running
`./gradlew :sub:checkProjectArtifact` therefore checks `:sub`'s effective POM, overrides included.

## Metadata Validation

> [!IMPORTANT]
> The task validates **the metadata you configured through this plugin** (`mergedDefaults`), not
> the XML of the POM that will actually be uploaded. This plugin does not write `mergedDefaults`
> into `MavenPublication.pom` for you — see the "Integration" section of the
> [README](../README.md) for wiring it up. If you configure
> `rootProjectPom` but never feed it into your publication, `checkProjectArtifact` passes while
> the published POM is still empty.

Against that metadata, every field Maven Central requires is checked
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
3. The generated Gradle Module Metadata (`module.json`) is signed too, when it was produced.
   It is published and checked at upload alongside the POM. Builds that disable
   `GenerateModuleMetadata` are unaffected.
4. Each signature file parses as a well-formed detached PGP signature (using BouncyCastle).

A signature is accepted **only at the sibling path** — `foo-1.0.jar` is paired with
`foo-1.0.jar.asc` sitting in the same directory, and with nothing else. Gradle always writes a
signature beside the file it signs, so nothing else is ever the right answer, and looser matching
has two ways to go wrong:

- matching by prefix lets `foo-1.0-sources.jar.asc` stand in for the main jar;
- matching by bare name is unsafe even when there is exactly one candidate. Every publication
  writes its POM to `build/publications/<name>/pom-default.xml`, so a build that signs one
  publication and not another leaves the unsigned one with a single same-named
  `pom-default.xml.asc` nearby — belonging to a different publication.

Same-named files found elsewhere are listed in the "signature not found" message so the cause is
visible, but they are never accepted as a match.

Anything the plugin cannot read is reported as a failure rather than passed over. `PgpSignatureVerifier`
returns a verdict instead of logging, so a signature file that is empty, lacks the armor markers,
is truncated, or does not parse as a signature list all produce
`SignatureVerification.failed(...)` — never a silent pass.

### Current limitations

The signature check validates **structure**, not cryptographic validity: the plugin does not yet
verify a signature against a public key, so it cannot detect a well-formed signature produced over
different content. Full verification is tracked in
[#22](https://github.com/YongGoose/Maven-Central-utility-plugins-for-Gradle/issues/22).

`signing { setRequired(false) }` skips signature verification entirely, with a warning — only the
metadata checks run in that configuration. The same is true when the project declares no
publications. In both cases the task says so explicitly rather than reporting the signatures as
verified:

```
✅ ArtifactCheckPlugin: metadata validation passed. PGP signature verification was SKIPPED
   (see the warnings above) — this run does not confirm the artifacts are signed.
```

On a machine with **no usable signing key** (a contributor without GPG keys, or CI without the
key), signing itself fails first and you get Gradle's own message rather than a validation report:

```
> Cannot perform signing task ':signMavenPublication' because it has no configured signatory
```
```
gpg: signing failed: No secret key
```

That is unavoidable: the task has to depend on the `Sign` tasks for the signatures to exist at
all, and there is no reliable way to tell in advance whether a configured signatory can actually
sign — `useGpgCmd()` reports one whether or not a secret key is present. To run the metadata
checks on such a machine, turn signing off:

```kotlin
signing {
    setRequired(false)
}
```

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
