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
    id("io.github.yonggoose.maven.central.utility.plugin.project") version "0.1.6"
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

## Technical Implementation

The plugin is implemented via the `OrganizationDefaultsProjectPlugin` class and stores all POM metadata in the `OrganizationDefaults` data class.

The `rootProjectPom` configuration in the root project is stored in the project’s `extraProperties` as `mergedDefaults`, making it accessible from all submodules:

```kotlin
val pom = project.rootProject.extensions.extraProperties.get("mergedDefaults") as OrganizationDefaults
```

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
