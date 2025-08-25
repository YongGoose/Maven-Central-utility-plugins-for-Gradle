# Kotlin Pom Gradle Plugin

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.0%2B-blue.svg)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-Apache--2.0-green.svg)](https://opensource.org/licenses/Apache-2.0)

A Gradle plugin that makes managing Maven POM metadata **simple and consistent** across multi-module projects.  

---

## 🚀 Features
- Centralized **organization-wide POM management**
- **Selective override** per submodule
- **Maven POM-compatible structure** (`groupId`, `artifactId`, `version`, licenses, etc.)
- **Artifact signing & validation** before publishing to Maven Central

---

## 📦 Installation

### Plugin

```kotlin
plugins {
  id("io.github.yonggoose.Maven-Central-utility-plugins-for-Gradle.project") version "0.1.7" // Gradle plugin to apply organization-wide defaults to projects.
  id("io.github.yonggoose.Maven-Central-utility-plugins-for-Gradle.setting") version "0.1.7" // Gradle plugin to apply organization-wide defaults to settings.
  id("io.github.yonggoose.Maven-Central-utility-plugins-for-Gradle.check") version "0.1.7" // Gradle plugin to check artifacts.
}
```

### Dependency
Not yet published to Maven Central. (Will be available soon.)

---
## ⚡ Quick Start
Minimal setup in `build.gradle.kts`:
```kotlin
rootProjectPom {
    groupId = "io.github.yonggoose"
    artifactId = "my-project"
    version = "1.0.0"
    name = "My Project"
    description = "A sample project"
}
```

Validate before publishing:
```kotlin
./gradlew checkProjectArtifact
```

## 🔗 Integration

### Integration with Vanniktech Maven Publish Plugin

The [Gradle Maven Publish Plugin (vanniktech)](https://github.com/vanniktech/gradle-maven-publish-plugin) is a popular choice for publishing Android and Kotlin libraries to **Maven Central, JCenter, and Nexus repositories**.

`Maven-Central-utility-plugins-for-Gradle` works seamlessly with it, eliminating the need to duplicate POM configurations across modules.

```kotlin
import io.github.yonggoose.organizationdefaults.OrganizationDefaults

plugins {
    id("java")
    id("io.github.yonggoose.Maven-Central-utility-plugins-for-Gradle.project") version "0.1.6"
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

This integration shows how **centralized POM management** from **Maven-Central-utility-plugins-for-Gradle**
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