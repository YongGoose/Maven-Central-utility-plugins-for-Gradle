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
        create("organizationDefaults") {
            id = "io.github.YongGoose.organization-defaults"
            implementationClass = "io.github.YongGoose.OrganizationDefaultsSettingsPlugin"
        }
        create("organizationDefaultsProject") {
            id = "io.github.YongGoose.organization-defaults-project"
            implementationClass = "io.github.YongGoose.OrganizationDefaultsProjectPlugin"
        }
    }
}