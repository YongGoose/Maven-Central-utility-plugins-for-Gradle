import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * `${'$'}{artifactId}` and friends in a template declared once, resolved per module.
 *
 * Note the backslash in every build script below. In `build.gradle.kts` a bare `${'$'}{artifactId}`
 * is **Kotlin string interpolation**, and it does not fail to compile — inside
 * `rootProjectPom { scm { … } }` it resolves against the enclosing extension's own `artifactId`
 * property and silently produces `.../null`. `\${'$'}{artifactId}` is what reaches the plugin.
 *
 * In this file that literal is written `\${'$'}{artifactId}`, since the test source is Kotlin too.
 */
class PomPlaceholderTest {

    @TempDir
    lateinit var projectDir: Path

    private fun writeSettings(vararg modules: String) {
        projectDir.resolve("settings.gradle.kts").toFile().writeText(
            """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }

            rootProject.name = "root"
            ${modules.joinToString("\n") { "include(\"$it\")" }}
            """.trimIndent()
        )
    }

    private fun writeModule(name: String, body: String) {
        projectDir.resolve(name).toFile().mkdirs()
        projectDir.resolve("$name/build.gradle.kts").toFile().writeText(
            """
            import io.github.yonggoose.organizationdefaults.OrganizationDefaults

            plugins {
                id("io.github.yonggoose.maven.central.utility.plugin.project")
            }

            $body
            """.trimIndent()
        )
    }

    /**
     * The scenario #32 opens with: one SCM block at the root, each module getting its own URLs.
     *
     * Two modules rather than one, because a single module cannot tell "resolved against the
     * module" from "resolved against the root" whenever the two happen to agree. The root's own
     * `artifactId` is `parent`, so a template resolved at the wrong layer produces `.../parent`
     * for both.
     */
    @Test
    fun `one root template gives every module its own scm urls`() {
        writeSettings("core", "api")

        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                id("io.github.yonggoose.maven.central.utility.plugin.project")
            }

            rootProjectPom {
                groupId = "io.github.yonggoose"
                artifactId = "parent"
                version = "1.0.0"

                name = "\${'$'}{artifactId}"
                url = "https://github.com/YongGoose/\${'$'}{artifactId}"

                scm {
                    url = "https://github.com/YongGoose/\${'$'}{artifactId}"
                    connection = "scm:git:git@github.com:YongGoose/\${'$'}{artifactId}.git"
                    developerConnection = "scm:git:git@github.com:YongGoose/\${'$'}{artifactId}.git"
                }
            }
            """.trimIndent()
        )

        listOf("core", "api").forEach { module ->
            writeModule(
                module,
                """
                projectPom {
                    artifactId = "$module"
                }

                tasks.register("verifyPom") {
                    doLast {
                        val pom = project.extensions.extraProperties.get("mergedDefaults") as OrganizationDefaults

                        check(pom.artifactId == "$module") { "artifactId was ${'$'}{pom.artifactId}" }
                        check(pom.name == "$module") { "name was ${'$'}{pom.name}" }
                        check(pom.url == "https://github.com/YongGoose/$module") {
                            "url was ${'$'}{pom.url}"
                        }
                        check(pom.scm?.url == "https://github.com/YongGoose/$module") {
                            "scm.url was ${'$'}{pom.scm?.url}"
                        }
                        check(pom.scm?.connection == "scm:git:git@github.com:YongGoose/$module.git") {
                            "scm.connection was ${'$'}{pom.scm?.connection}"
                        }
                        check(pom.scm?.developerConnection == "scm:git:git@github.com:YongGoose/$module.git") {
                            "scm.developerConnection was ${'$'}{pom.scm?.developerConnection}"
                        }
                    }
                }
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("core:verifyPom", "api:verifyPom", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":core:verifyPom")?.outcome)
        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":api:verifyPom")?.outcome)
    }

    /**
     * A typo in a placeholder is the case that decides whether this feature helps or hurts.
     * Substituting it into something plausible would publish the mistake; leaving it in place and
     * saying so is what `checkProjectArtifact` is for.
     */
    @Test
    fun `a misspelled placeholder is reported instead of published`() {
        writeSettings()

        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                `maven-publish`
                signing
                id("io.github.yonggoose.maven.central.utility.plugin.project")
                id("io.github.yonggoose.maven.central.utility.plugin.check")
            }

            rootProjectPom {
                groupId = "io.github.yonggoose"
                artifactId = "library"
                version = "1.0.0"

                name = "Library"
                description = "A library"
                url = "https://github.com/YongGoose/library"

                licenses {
                    license {
                        name = "Apache-2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }

                developers {
                    developer {
                        id = "dev1"
                        name = "Developer1"
                    }
                }

                scm {
                    url = "https://github.com/YongGoose/\${'$'}{artifctId}"
                }
            }

            signing {
                setRequired(false)
            }
            """.trimIndent()
        )

        val failure = assertThrows<UnexpectedBuildFailure> {
            GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("checkProjectArtifact", "--stacktrace")
                .withPluginClasspath()
                .forwardOutput()
                .build()
        }

        assertValidationRejected(
            failure,
            "Unresolved placeholder in 'https://github.com/YongGoose/\${'$'}{artifctId}'"
        )
    }
}
