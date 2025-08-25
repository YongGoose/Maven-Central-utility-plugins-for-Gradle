plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.1"
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation("org.bouncycastle:bcpg-jdk18on:1.77")
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
}

kotlin {
    jvmToolchain(17)
}

tasks {
    test {
        useJUnitPlatform()
    }
}

gradlePlugin {
    website = "https://github.com/YongGoose/Maven-Central-utility-plugins-for-Gradlee"
    vcsUrl = "https://github.com/YongGoose/Maven-Central-utility-plugins-for-Gradle.git"

    plugins {
        create("organizationDefaultsProject") {
            id = "io.github.yonggoose.maven.central.utility.plugin.project"
            displayName = "Organization Defaults Project Plugin"
            description = "A Gradle plugin to apply organization-wide defaults to projects."
            tags = setOf("organization", "defaults")
            implementationClass = "io.github.yonggoose.organizationdefaults.OrganizationDefaultsProjectPlugin"
        }

        create("organizationDefaultsSetting") {
            id = "io.github.yonggoose.maven.central.utility.plugin.setting"
            displayName = "Organization Defaults Settings Plugin"
            description = "A Gradle plugin to apply organization-wide defaults to settings."
            tags = setOf("organization", "defaults", "settings")
            implementationClass = "io.github.yonggoose.organizationdefaults.OrganizationDefaultsSettingsPlugin"
        }

        create("artifactCheckProject") {
            id = "io.github.yonggoose.maven.central.utility.plugin.check"
            displayName = "Artifact Check Project Plugin"
            description = "A Gradle plugin to check artifacts against organization defaults."
            tags = setOf("organization", "defaults", "artifact", "check")
            implementationClass = "io.github.yonggoose.organizationdefaults.ArtifactCheckPluginForProject"
        }
    }
}