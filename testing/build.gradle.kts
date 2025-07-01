plugins {
    kotlin("jvm") version "1.9.23"
    id("java-gradle-plugin")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":plugins"))
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
}

gradlePlugin {
    testSourceSets(sourceSets.test.get())
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}