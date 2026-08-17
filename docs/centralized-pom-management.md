# Centralized POM Management

When publishing a project to Maven Central, POM (Project Object Model) metadata is mandatory. For multi-module projects, keeping this metadata consistent across modules is crucial.

## Problem

In multi-module projects, the following issues often occur:

- Each module must repeat the same POM information
- Organization-level changes require updating every module
- Maintaining consistency is difficult

## Solution

This plugin allows you to define organization-level POM metadata in the root project and automatically applies it to all submodules.

### Usage

In `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.yonggoose.maven.central.utility.plugin.project") version "0.2.0"
}

rootProjectPom {
    groupId = "io.github.yonggoose"
    artifactId = "organization-defaults"
    version = "1.0.0"
    
    name = "Test Organization"
    description = "Organization defaults plugin test"
    url = "https://example.org"
    
    licenses {
        license {
            name = "MIT License"
            url = "https://opensource.org/licenses/MIT"
            distribution = "repo"
        }
    }
    
    developers {
        developer {
            id = "dev1"
            name = "Developer1"
            email = "dev1@example.com"
            organization = "YongGoose"
            organizationUrl = "https://yonggoose.github.io"
        }
    }
    
    scm {
        url = "https://github.com/YongGoose/organization-defaults"
        connection = "scm:git:git@github.com:YongGoose/organization-defaults.git"
        developerConnection = "scm:git:git@github.com:YongGoose/organization-defaults.git"
    }
}
```

### Build-wide defaults in `settings.gradle.kts`

`rootProjectPom` lives on the root project, which means the root project has to carry publishing
metadata even when it publishes nothing itself. The **settings** plugin offers the same DSL one
level up, in `settings.gradle.kts`:

```kotlin
plugins {
    id("io.github.yonggoose.maven.central.utility.plugin.setting") version "0.2.0"
}

rootProjectSetting {
    groupId = "io.github.yonggoose"
    version = "1.0.0"

    licenses {
        license {
            name = "Apache-2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0"
        }
    }
}
```

`rootProjectSetting` is the **weakest** of the three levels: `rootProjectPom` overrides it, and
`projectPom` overrides both. Declaring the same field in both `rootProjectSetting` and
`rootProjectPom` is therefore redundant rather than additive — most builds pick one of the two.

Two things it does not do:

- It does not apply the project plugin for you. Every module whose `mergedDefaults` you read still
  needs `io.github.yonggoose.maven.central.utility.plugin.project`; without it there is no
  `mergedDefaults` entry to read, whatever `settings.gradle.kts` says.
- It is a settings plugin, so it can only be applied from `settings.gradle.kts`. Listing its id in
  a build script's `plugins { }` block fails to resolve.

### Templates: `${groupId}`, `${artifactId}`, `${version}`

A literal SCM URL is a poor organization-wide default — it differs per repository by exactly one
word. Those three coordinates can be referred to from any other field, and are substituted once the
levels have been merged:

```kotlin
rootProjectPom {
    groupId = "io.github.yonggoose"
    version = "1.0.0"

    url = "https://github.com/YongGoose/\${artifactId}"

    scm {
        url = "https://github.com/YongGoose/\${artifactId}"
        connection = "scm:git:git@github.com:YongGoose/\${artifactId}.git"
        developerConnection = "scm:git:git@github.com:YongGoose/\${artifactId}.git"
    }
}
```

```kotlin
// core/build.gradle.kts
projectPom {
    artifactId = "core"      // -> https://github.com/YongGoose/core
}
```

Substitution happens **after** the merge, so a template declared once picks up each module's own
`artifactId`. That is the whole point of it: resolving per extension would give every module the
root's coordinate.

> [!IMPORTANT]
> Note the **backslash**. In a `.kts` file, a bare `${artifactId}` is Kotlin string interpolation,
> and it does not fail to compile — inside `rootProjectPom { scm { … } }` it resolves against the
> extension's own `artifactId` property and quietly produces `https://github.com/YongGoose/null`.
> Write `\${artifactId}` so the placeholder reaches the plugin. In Groovy, single quotes do the
> same job: `'https://github.com/YongGoose/${artifactId}'`.

Anything else is left exactly as written — `$artifactId` without braces, `${a.b}`, a plain dollar
sign. Only those three names, and only when the merged POM sets them to something non-blank.

> [!NOTE]
> There is no escape for a **literal** `${identifier}` in POM text. `${...}` is placeholder syntax
> everywhere the plugin looks, so a description that genuinely needs to contain `${revision}` has
> to be reworded. This is deliberate: an escape would have to survive resolution to be checked and
> be gone by the time the POM is published, and those two cannot both be true in one pass. The
> alternative — only reporting the three known names — would let `${artifctId}` through, which is
> the mistake worth catching. Maven interpolates `${...}` in POMs itself, so a literal one is
> ambiguous there in any case.

A placeholder that could not be resolved — a misspelling, or a coordinate nothing set — is **not**
substituted with a guess. It stays put and `checkProjectArtifact` reports it:

```
Validation failed:
Unresolved placeholder in 'https://github.com/YongGoose/${artifctId}': only ${groupId},
${artifactId} and ${version} are substituted, and only when this POM sets them.
```

## Technical Implementation

The plugin is implemented via the `PomDefaultsProjectPlugin` class and stores all POM metadata in the `OrganizationDefaults` data class.

The plugin writes the result of merging `rootProjectSetting`, `rootProjectPom` and the module's own
`projectPom` — in that order, each overriding the previous — into **each project's**
`extraProperties` under the key `mergedDefaults`. Read it from the project you are configuring, not
from the root — the root's entry does not contain that module's overrides:

```kotlin
val pom = project.extensions.extraProperties.get("mergedDefaults") as OrganizationDefaults
```

For that to work, **every module that reads `mergedDefaults` must also apply the plugin** — it is
what creates the entry (and the module's own `projectPom` block). Applying it only in the root
leaves submodules without one:

```kotlin
// sub/build.gradle.kts
plugins {
    id("io.github.yonggoose.maven.central.utility.plugin.project")
}
```

For a multi-module build you can apply it once in the root's `plugins { }` block and hand it to
the submodules from there. The root must go through `plugins { }` — that is both what puts the
plugin on the script's classpath and what makes the `rootProjectPom { }` accessor available:

```kotlin
// build.gradle.kts
plugins {
    id("io.github.yonggoose.maven.central.utility.plugin.project") version "0.2.0"
}

subprojects {
    apply(plugin = "io.github.yonggoose.maven.central.utility.plugin.project")
}

rootProjectPom {
    // organization-wide defaults
}
```

A submodule that applies the plugin this way has no generated accessor, so it configures its own
overrides through `configure<PomDefaultsExtension> { }` rather than `projectPom { }`, and needs the
import:

```kotlin
// sub/build.gradle.kts
import io.github.yonggoose.organizationdefaults.PomDefaultsExtension

configure<PomDefaultsExtension> {
    artifactId = "child-module"
}
```

Declaring the plugin in each submodule's own `plugins { }` block keeps the nicer `projectPom { }`
syntax and needs no import.

## Supported POM Elements
- groupId, artifactId, version 
- name, description, url, inceptionYear 
- licenses – multiple license entries 
- developers – developer information 
- organization – organization details 
- scm – source control information 
- issueManagement – issue tracker details 
- mailingLists – mailing list details

For overrides in submodules, please refer to the following [documentation](/docs/selective-override.md).
