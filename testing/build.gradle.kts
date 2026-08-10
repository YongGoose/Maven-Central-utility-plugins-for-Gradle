plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
}

dependencies {
    implementation(project(":plugins"))
    testImplementation(gradleTestKit())
    // Generates the throwaway signing key ArtifactCheckSignedTest feeds to useInMemoryPgpKeys.
    testImplementation("org.bouncycastle:bcpg-jdk18on:1.77")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
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
