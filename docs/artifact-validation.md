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
result of merging `rootProjectSetting`, `rootProjectPom` and that module's `projectPom`. Running
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

1. A signature exists for every artifact in each `MavenPublication`.
2. The generated POM is signed too.
3. The generated Gradle Module Metadata (`module.json`) is signed too, when it is part of the
   publication. It is published and checked at upload alongside the POM. Builds that disable
   `GenerateModuleMetadata` are unaffected.
4. Each signature parses as a well-formed detached PGP signature (using BouncyCastle), and — when
   a public key is configured — actually verifies against the bytes it covers.

### Verifying against a public key

Point the check at the public half of the key that signs your artifacts, and every signature is
verified cryptographically rather than merely parsed:

```kotlin
artifactCheck {
    publicKeyRing = file("pubring.asc")          // gpg --armor --export <key-id> > pubring.asc
    // or, for CI:
    // inMemoryPublicKey = providers.environmentVariable("SIGNING_PUBLIC_KEY")
}
```

Set one or the other, not both — with both the build stops rather than picking one.

The key has to be supplied explicitly because the plugin cannot derive it. The `signing` plugin
holds a *secret* key, and its three configuration forms do not lead back to a public key the same
way: `useGpgCmd()` hands signing to an external process that never exposes one.

Two failures become visible that the structural check cannot see:

| Situation | Report |
|---|---|
| The file changed after it was signed | `The signature for … does not match its contents.` |
| A different key signed it | `… is signed with key 0x…, which is not in 'pubring.asc'.` |

A key ring that is configured but unreadable, or missing from disk, **fails the build**. It does
not fall back to the structural check — configuring verification and silently getting none is the
outcome this task exists to prevent.

With no key configured the check is structural only, as before, and the **verdict itself** says so
rather than reading `verified successfully`:

```
✅ ArtifactCheckPlugin: metadata validation passed and every PGP signature parsed, but no public
   key is configured — this run does not confirm they were made over these files.
```

That wording matters for the CI recipe above. If `SIGNING_PUBLIC_KEY` is not set — a fork build, a
renamed secret, a typo — the provider simply has no value, which the plugin cannot tell apart from
never having configured a key at all. The verdict line is what makes the difference visible. A
secret that expands to an **empty** string *is* distinguishable, and fails the build.

Each file is paired with its signature using the mapping **Gradle itself records**
(`Signature.toSign`), not by deriving a name or a path. Two things follow:

- The check makes no assumption about the signature's extension, so a build that sets
  `signing { signatureType = BinarySignatureType() }` and emits `.sig` works the same way.
- A signature can only ever be attributed to the file Gradle actually signed, so the wrong-file
  pairings that name- or prefix-based matching allows — a jar checked against the sources jar's
  signature, or one publication's `pom-default.xml.asc` standing in for another's — are impossible
  by construction rather than guarded against.

Only signatures **this build produced** count. A `Sign` task that did not run — skipped by its
`onlyIf`, disabled, or excluded with `-x signMavenPublication` — contributes nothing, even when an
`.asc` file from an earlier run is still sitting in `build/`. Otherwise a dirty `build/` directory
would report `PGP signatures verified successfully` for artifacts that were just rebuilt
underneath a stale signature, which is the fail-open reporting this task exists to remove. The
generated POM and module metadata are decided the same way, by their producing task's outcome.

The flip side is that **signatures the Gradle `signing` plugin did not create are invisible**. If
you sign artifacts with an external tool and drop the files into `build/`, `checkProjectArtifact`
reports them as unsigned.

Anything the plugin cannot read is reported as a failure rather than passed over.
`PgpSignatureVerifier` returns a verdict instead of logging, so a signature that is empty,
truncated, or does not parse as a signature list all produce `SignatureVerification.failed(...)`
— never a silent pass.

### Current limitations

`checkProjectArtifact` is **not configuration-cache compatible**, and declares it rather than
leaving the build to discover it. With `--configuration-cache` the run succeeds and the cache
entry is thrown away:

```
1 problem was found storing the configuration cache.
- Task ':checkProjectArtifact' of type 'org.gradle.api.DefaultTask': cannot serialize object
  of type 'org.gradle.api.internal.project.DefaultProject' …

BUILD SUCCESSFUL
Configuration cache entry discarded with 1 problem.
```

The problem is still reported — it is real — but it is no longer fatal, which it was before the
task declared itself. Only invocations that schedule this task lose caching; the rest of the build
is unaffected.

The reason is the freshness rule above. Whether a signature belongs to *this* build is answered by
its `Sign` task's outcome, and task outcomes do not exist before execution — while the
configuration cache requires everything a task action reads to be known by then. Two ways of
deciding it in advance were tried and rejected: reading the task graph cannot see `onlyIf`, so
stale signatures would have been reported as verified, and a build-event listener sees outcomes
exactly but is delivered asynchronously, so a correctly signed build could intermittently report
that nothing was signed. Neither trade was worth making on a signature check. Tracked in
[#43](https://github.com/YongGoose/Maven-Central-utility-plugins-for-Gradle/issues/43).

Without an `artifactCheck` public key the signature check validates **structure** only, so it
cannot tell a well-formed signature produced over different content from a correct one. See
[Verifying against a public key](#verifying-against-a-public-key) for turning that into a real
verification. What is still not checked either way is whether the key is one Maven Central will
accept — that it is published to a keyserver, unexpired and unrevoked.

`signing { setRequired(false) }` means an **unsigned file is not an error**. What the task does
then depends on what got signed anyway:

| Situation | Result |
|---|---|
| Nothing was signed | Signature verification is skipped entirely; only the metadata checks run. |
| Some files were signed | Those signatures are verified; unsigned files are reported as warnings. |
| A signature exists but is broken | **Still an error.** Nobody opts into a corrupt signature. |

The same skip applies when the project declares no publications. In every case the task states
which of these happened rather than reporting the signatures as verified:

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
sign — `useGpgCmd()` reports one whether or not a secret key is present.

How you get a metadata-only run depends on whether a **signatory** is configured, because Gradle
skips a `Sign` task only when `!required && signatory == null`:

| Your setup on that machine | What to do |
|---|---|
| No signatory (no `useGpgCmd()`, no `signing.keyId`/`secretKeyRingFile`, no in-memory keys) | `signing { setRequired(false) }` — Gradle skips the `Sign` tasks and the metadata checks run. |
| A signatory is configured but cannot sign (`useGpgCmd()` with no secret key) | `setRequired(false)` is **not** enough: `signatory != null` keeps `Sign` in the graph and gpg fails. Exclude the signing tasks as well: `./gradlew checkProjectArtifact -x signMavenPublication` (with `setRequired(false)` so the missing signatures are warnings, not errors). |

The second row is the common CI-without-the-key shape. If that is awkward for your build, keep the
signatory configuration behind a condition so it is simply absent where the key is.

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
