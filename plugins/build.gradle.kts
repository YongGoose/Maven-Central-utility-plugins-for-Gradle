plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.1"
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(project(":core"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    website = "https://github.com/YongGoose/kotlin-pom-gradle"
    vcsUrl = "https://github.com/YongGoose/kotlin-pom-gradle.git"

    plugins {
        plugins {
            create("organizationDefaultsProject") {
                id = "io.github.yonggoose.organization-defaults-project"
                displayName = "Organization Defaults Project Plugin"
                description = "A Gradle plugin to apply organization-wide defaults to projects."
                tags = setOf("gradle", "plugin", "organization", "defaults")
                implementationClass = "io.github.yonggoose.organizationdefaults.OrganizationDefaultsProjectPlugin"
            }

            create("organizationDefaultsSetting") {
                id = "io.github.yonggoose.organization-defaults-setting"
                displayName = "Organization Defaults Settings Plugin"
                description = "A Gradle plugin to apply organization-wide defaults to settings."
                tags = setOf("gradle", "plugin", "organization", "defaults", "settings")
                implementationClass = "io.github.yonggoose.organizationdefaults.OrganizationDefaultsSettingsPlugin"
            }

            create("artifactCheckProject") {
                id = "io.github.yonggoose.organization-defaults-artifact-check-project"
                displayName = "Artifact Check Project Plugin"
                description = "A Gradle plugin to check artifacts against organization defaults."
                tags = setOf("gradle", "plugin", "organization", "defaults", "artifact", "check")
                implementationClass = "io.github.yonggoose.organizationdefaults.ArtifactCheckPluginForProject"
            }
        }
    }
}