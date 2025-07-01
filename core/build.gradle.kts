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
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
