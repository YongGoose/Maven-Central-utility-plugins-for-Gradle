plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
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
    }
}