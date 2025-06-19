plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `kotlin-dsl`
}

group = "com.example"
version = "1.0-SNAPSHOT"

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
            id = "com.your.organization-defaults"
            implementationClass = "OrganizationDefaultsSettingsPlugin"
        }
    }
}