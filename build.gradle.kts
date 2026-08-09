import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
}

allprojects {
    group = "io.github.yonggoose"
    version = "0.1.7"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java")
    // Without this, ktlintCheck only ever looked at the root project's .kts files -- the root has
    // no Kotlin source set, so plugins/ and testing/ were never linted at all.
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    dependencies {
        "implementation"(kotlin("stdlib"))
        "testImplementation"(kotlin("test"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.set(listOf("-Xjsr305=strict"))
        }
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
