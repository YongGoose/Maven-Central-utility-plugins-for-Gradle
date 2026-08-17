import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.10" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
}

/**
 * Passed straight to ktlint rather than left to `.editorconfig` discovery, so the ruleset is
 * identical in the IDE, on a developer machine and in CI. Applied to the root project and to
 * every subproject.
 */
val ktlintSettings = mapOf(
    // ktlint 1.x defaults to `ktlint_official`, whose forced signature wrapping this codebase
    // does not follow.
    "ktlint_code_style" to "intellij_idea",
    // `ktlint_code_style` only selects the ruleset when it comes from a real .editorconfig, so
    // switch this one off by name: it dictates exactly how a signature must wrap and how an
    // expression body must follow it, which is a house style this project has not adopted.
    "ktlint_standard_function-signature" to "disabled",
    // IntelliJ permits trailing commas, and ktlint turns "permitted" into "required".
    // This codebase does not use them.
    "ij_kotlin_allow_trailing_comma" to "false",
    "ij_kotlin_allow_trailing_comma_on_call_site" to "false",
    "max_line_length" to "140"
)

ktlint {
    additionalEditorconfig.set(ktlintSettings)
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

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        additionalEditorconfig.set(ktlintSettings)
    }

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
