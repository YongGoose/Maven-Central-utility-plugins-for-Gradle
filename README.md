# Maven Central utility plugins for Gradle

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.0%2B-blue.svg)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-Apache--2.0-green.svg)](https://opensource.org/licenses/Apache-2.0)

A Gradle plugin that makes managing Maven POM metadata **simple and consistent** across multi-module projects.  

---

## 🚀 Features
- Centralized **organization-wide POM management** / [Guide](docs/centralized-pom-management.md)
- **Selective override** per submodule / [Guide](docs/selective-override.md)
- **Artifact signing & validation** before publishing to Maven Central / [Guide](docs/artifact-validation.md)

---

## 📦 Installation

### Plugin

```kotlin
plugins {
  id("io.github.yonggoose.maven.central.utility.plugin.project") version "0.1.7" // Gradle plugin to apply organization-wide defaults to projects.
  id("io.github.yonggoose.maven.central.utility.plugin.setting") version "0.1.7" // Gradle plugin to apply organization-wide defaults to settings.
  id("io.github.yonggoose.maven.central.utility.plugin.check") version "0.1.7" // Gradle plugin to check artifacts.
}
```

### Dependency
Not yet published to Maven Central. (Will be available soon.)

---
## ⚡ Quick Start
Setup in `build.gradle.kts`. The metadata below is exactly the set Maven Central requires, which is
also what `checkProjectArtifact` looks for:
```kotlin
plugins {
    `maven-publish`
    signing
    id("io.github.yonggoose.maven.central.utility.plugin.project") version "0.1.7"
    id("io.github.yonggoose.maven.central.utility.plugin.check") version "0.1.7"
}

rootProjectPom {
    groupId = "io.github.yonggoose"
    artifactId = "my-project"
    version = "1.0.0"

    name = "My Project"
    description = "A sample project"
    url = "https://github.com/YongGoose/my-project"

    licenses {
        license {
            name = "Apache-2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0"
        }
    }

    developers {
        developer {
            id = "yonggoose"
            name = "Yongjun Hong"
        }
    }

    scm {
        url = "https://github.com/YongGoose/my-project"
        connection = "scm:git:git@github.com:YongGoose/my-project.git"
        developerConnection = "scm:git:git@github.com:YongGoose/my-project.git"
    }
}
```

Validate before publishing:
```bash
./gradlew checkProjectArtifact
```

The task depends on the `Sign` tasks, so on a machine with no signing key Gradle's signing plugin
fails before any report is produced. Add `signing { setRequired(false) }` there to get a
metadata-only run. See the [artifact validation guide](docs/artifact-validation.md) for what is
checked and what is skipped.

## 🔗 Integration

### Integration with Vanniktech Maven Publish Plugin

The [Gradle Maven Publish Plugin (vanniktech)](https://github.com/vanniktech/gradle-maven-publish-plugin) is a popular choice for publishing Android and Kotlin libraries to **Maven Central, JCenter, and Nexus repositories**.

`Maven Central utility plugins for Gradle` works seamlessly with it, eliminating the need to duplicate POM configurations across modules.

```kotlin
import io.github.yonggoose.organizationdefaults.OrganizationDefaults

plugins {
    id("java")
    id("io.github.yonggoose.maven.central.utility.plugin.project") version "0.1.7"
    id("com.vanniktech.maven.publish") version "0.34.0"
    id("maven-publish")
}

rootProjectPom {
    groupId = "io.github.yonggoose"
    artifactId = "organization-defaults"
    version = "1.0.0"
    ...
}

afterEvaluate {
    val mergedPom = project.extensions.extraProperties.get("mergedDefaults") as OrganizationDefaults

    mavenPublishing {
        coordinates(
            groupId = mergedPom.groupId,
            artifactId = mergedPom.artifactId,
            version = mergedPom.version
        )

        pom {
            name.set(mergedPom.name)
            description.set(mergedPom.description)
            url.set(mergedPom.url)
            ...
        }
    }
}
```

This integration shows how **centralized POM management** from **Maven Central utility plugins for Gradle**
can be directly reused inside **vanniktech-maven-publish**,
making your publishing workflow cleaner and less error-prone.

---

## 📚 Documentation
➡️ [Blog Post](https://dev.to/gradle-community/centralized-pom-configuration-management-with-kotlin-pom-gradle-1kap) | [Demo Video](https://drive.google.com/file/d/1McNXyBdIQpEPqTn2ZRjnYJ4E8JNwHMZE/view)

## ✅ Requirements
- Gradle 8.0+
- Kotlin DSL support

## 📄 License
Apache License 2.0