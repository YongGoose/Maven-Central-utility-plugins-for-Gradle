plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
    `kotlin-dsl`
}

repositories {
    mavenCentral()
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
    plugins {
        create("organizationDefaultsProject") {
            id = "io.github.yonggoose.organization-defaults-project"
            implementationClass = "io.github.yonggoose.organizationdefaults.OrganizationDefaultsProjectPlugin"
        }

        create("organizationDefaultsSetting") {
            id = "io.github.yonggoose.organization-defaults-setting"
            implementationClass = "io.github.yonggoose.organizationdefaults.OrganizationDefaultsSettingsPlugin"
        }

        create("artifactCheckProject") {
            id = "io.github.yonggoose.organization-defaults-artifact-check-project"
            implementationClass = "io.github.yonggoose.organizationdefaults.ArtifactCheckPluginForProject"
        }
    }
}