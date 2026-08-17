# Changelog

## 0.2.0

Three features that were documented but did not work, and one that could not be run.

### The settings plugin now does something

`rootProjectSetting { }` was inert. `OrganizationDefaultsSettingsPlugin` stored what it configured
into a shared build service, and nothing ever read that service back — metadata declared in
`settings.gradle.kts` reached no POM, no `mergedDefaults` entry and no `checkProjectArtifact`
verdict.

There are now three levels, each overriding the one before it:

| Level | Block | Declared in |
|---|---|---|
| 1 | `rootProjectSetting { }` | `settings.gradle.kts` |
| 2 | `rootProjectPom { }` | the root project |
| 3 | `projectPom { }` | each module |

The installation snippet also listed the settings plugin id in a `build.gradle.kts` `plugins { }`
block, where it cannot resolve.

*(#59, closes #2)*

### Signatures are verified against a public key

The check parsed a signature and called it verified, so a well-formed signature over *different
content* passed — the one thing a signature check is for.

```kotlin
artifactCheck {
    publicKeyRing = file("pubring.asc")          // gpg --armor --export <key-id>
    // or, for CI -- one or the other, never both:
    // inMemoryPublicKey = providers.environmentVariable("SIGNING_PUBLIC_KEY")
}
```

Two failures become visible: a file changed after it was signed, and a signature made by a key the
ring does not hold. Anything configured-but-unusable — a blank in-memory key, a missing or
unreadable ring, both sources at once — fails the build rather than quietly falling back.

Where no key is configured the behaviour is unchanged, and **the report no longer says otherwise**:
the verdict and the per-file `--info` line each distinguish a signature that was *verified* from
one that merely *parsed*.

*(#67, closes #22)*

### `${groupId}`, `${artifactId}` and `${version}` in POM text

An SCM block is a poor organization-wide default; it differs per repository by one word.

```kotlin
rootProjectPom {
    scm {
        url = "https://github.com/YongGoose/\${artifactId}"
    }
}
```

Substitution runs on the **merged** POM, so a template declared once picks up each module's own
`artifactId`. An unresolvable or misspelled placeholder is reported rather than substituted into
something plausible.

Mind the backslash: in a `.kts` file a bare `${artifactId}` is Kotlin interpolation, does not fail
to compile, and quietly produces `.../null`.

*(#69, closes #32. Replaces #58 — thanks @webbrain-one.)*

### `--configuration-cache` no longer fails the build

`checkProjectArtifact` used to end in

```
cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject'
```

so a consumer who turned the cache on could not run the task at all. It now declares the
incompatibility: the run succeeds and the entry is discarded. The task is still not
configuration-cache compatible — #43 stays open for that — because the two ways of getting there
both cost accuracy, and accuracy is what this task is.

*(#66)*

### Renames

| Now | Was |
|---|---|
| `ArtifactCheckPlugin` | `ArtifactCheckPluginForProject` |
| `PomDefaultsProjectPlugin` | `OrganizationDefaultsProjectPlugin` |
| `PomDefaultsSettingsPlugin` | `OrganizationDefaultsSettingsPlugin` |
| `PomDefaultsSettingsExtension` | `OrganizationDefaultsExtension` |
| `PomDefaultsService` | `OrganizationDefaultsService` |
| `PomDefaultsParameters` | `OrganizationDefaultsParameters` |

**No action required.** Plugin classes are reached through their ids. The other three keep
deprecated `typealias`es, due for removal in 0.3.0 — 0.2.0 is the release that introduces the new
names, so the aliases have to survive one version for the warning to be worth anything.

`OrganizationDefaults` itself and the package are unchanged. They appear in the cast every
documented integration copies, and they are the remaining half of #14.

*(#68, part of #14)*

### Toolchain

Gradle **8.14 → 9.7.0** and Kotlin **2.0.21 → 2.4.10**, which had to move together: Gradle 9's
`kotlin-dsl` calls a KGP method 2.0.21 does not have, and Gradle 8.14's `kotlin-dsl` pins a
language version 2.4 has dropped. Also ktlint plugin 14.2.0, JUnit 6.1.3, BouncyCastle 1.85
(bcprov 1.85.2), and the GitHub Actions in CI.

*(#63, #64, #65, and the dependabot batch #44–#52)*

### Validation, and tests that can fail

Carried over from #42, which landed after 0.1.7 was tagged:

- Signatures are paired with their files through Gradle's own `Signature.toSign`, not by deriving
  a name — a `.sig` from `BinarySignatureType()` works, and wrong-file pairings are impossible
  rather than guarded against.
- Only signatures **this build produced** count. A `Sign` task that did not run contributes
  nothing, even with an `.asc` still sitting in `build/`.
- The POM and Gradle Module Metadata are signed and checked too.
- `PgpSignatureVerifier` returns a verdict instead of logging, so an empty, truncated or
  unparseable signature is a failure and never a silent pass.

Test count over this release: **42 → 66**.

*(#42)*

## 0.1.7 and earlier

No changelog was kept. See the
[releases](https://github.com/YongGoose/Maven-Central-utility-plugins-for-Gradle/releases).
