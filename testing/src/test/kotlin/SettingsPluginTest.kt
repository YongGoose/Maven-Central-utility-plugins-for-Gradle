import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * What `rootProjectSetting { }` in `settings.gradle.kts` is worth to a build.
 *
 * These assert the user-visible result — the metadata a module ends up with in `mergedDefaults` —
 * rather than that the settings plugin stored something somewhere. The predecessor of this file
 * (`ExtensionTest`) pulled the build service out of `gradle.sharedServices` by hand and asserted
 * the round trip, and so stayed green for the entire time the project plugin ignored that service
 * and `rootProjectSetting` changed nothing at all.
 */
class SettingsPluginTest {

    @TempDir
    lateinit var projectDir: Path

    private fun writeSubProject(body: String) {
        projectDir.resolve("sub").toFile().mkdirs()
        projectDir.resolve("sub/build.gradle.kts").toFile().writeText(
            """
            import io.github.yonggoose.organizationdefaults.OrganizationDefaults

            plugins {
                id("io.github.yonggoose.maven.central.utility.plugin.project")
            }

            $body
            """.trimIndent()
        )
    }

    private fun runVerifyPom(): org.gradle.testkit.runner.BuildResult =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("sub:verifyPom", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

    @Test
    fun `metadata configured in settings reaches a submodule's merged defaults`() {
        projectDir.resolve("settings.gradle.kts").toFile().writeText(
            """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }

            plugins {
                id("io.github.yonggoose.maven.central.utility.plugin.setting")
            }

            ${PomFixture.pomBlock(extensionName = "rootProjectSetting")}

            rootProject.name = "root"
            include("sub")
            """.trimIndent()
        )

        // No `rootProjectPom { }` here on purpose: settings has to be able to carry a build on its
        // own, otherwise the settings plugin is still just an alias for the root project's block.
        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                id("io.github.yonggoose.maven.central.utility.plugin.project")
            }
            """.trimIndent()
        )

        writeSubProject(
            """
            tasks.register("verifyPom") {
                doLast {
                    val pom = project.extensions.extraProperties.get("mergedDefaults") as OrganizationDefaults

                    check(pom.groupId == "io.github.yonggoose") { "groupId was ${'$'}{pom.groupId}" }
                    check(pom.artifactId == "organization-defaults") { "artifactId was ${'$'}{pom.artifactId}" }
                    check(pom.version == "1.0.0") { "version was ${'$'}{pom.version}" }
                    check(pom.name == "Test Organization") { "name was ${'$'}{pom.name}" }
                    check(pom.description == "Organization defaults plugin test") {
                        "description was ${'$'}{pom.description}"
                    }
                    check(pom.url == "https://example.org") { "url was ${'$'}{pom.url}" }

                    check(pom.licenses.map { it.name } == listOf("Apache-2.0")) {
                        "licenses were ${'$'}{pom.licenses}"
                    }
                    check(pom.developers.map { it.id } == listOf("dev1")) {
                        "developers were ${'$'}{pom.developers}"
                    }
                    check(pom.scm?.url == "https://github.com/YongGoose/organization-defaults") {
                        "scm.url was ${'$'}{pom.scm?.url}"
                    }
                }
            }
            """.trimIndent()
        )

        Assertions.assertEquals(TaskOutcome.SUCCESS, runVerifyPom().task(":sub:verifyPom")?.outcome)
    }

    /**
     * The precedence the three layers are documented to have, in one build:
     * `rootProjectSetting` < `rootProjectPom` < `projectPom`.
     *
     * Every field below is set by a different subset of the three, so a merge that got the order
     * wrong — or that dropped a layer — lands on a different value for at least one of them.
     */
    @Test
    fun `each layer overrides the one before it`() {
        projectDir.resolve("settings.gradle.kts").toFile().writeText(
            """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }

            plugins {
                id("io.github.yonggoose.maven.central.utility.plugin.setting")
            }

            rootProjectSetting {
                groupId = "io.github.yonggoose"
                artifactId = "from-settings"
                version = "1.0.0"

                name = "From settings"
                description = "From settings"
                url = "https://settings.example.org"

                scm {
                    url = "https://settings.example.org/scm"
                }
            }

            rootProject.name = "root"
            include("sub")
            """.trimIndent()
        )

        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                id("io.github.yonggoose.maven.central.utility.plugin.project")
            }

            rootProjectPom {
                name = "From root project"
                url = "https://root.example.org"
            }
            """.trimIndent()
        )

        writeSubProject(
            """
            projectPom {
                artifactId = "from-sub"
                name = "From sub project"
            }

            tasks.register("verifyPom") {
                doLast {
                    val pom = project.extensions.extraProperties.get("mergedDefaults") as OrganizationDefaults

                    // Only settings set these.
                    check(pom.groupId == "io.github.yonggoose") { "groupId was ${'$'}{pom.groupId}" }
                    check(pom.version == "1.0.0") { "version was ${'$'}{pom.version}" }
                    check(pom.description == "From settings") { "description was ${'$'}{pom.description}" }
                    check(pom.scm?.url == "https://settings.example.org/scm") { "scm.url was ${'$'}{pom.scm?.url}" }

                    // rootProjectPom beats settings.
                    check(pom.url == "https://root.example.org") { "url was ${'$'}{pom.url}" }

                    // projectPom beats both.
                    check(pom.artifactId == "from-sub") { "artifactId was ${'$'}{pom.artifactId}" }
                    check(pom.name == "From sub project") { "name was ${'$'}{pom.name}" }
                }
            }
            """.trimIndent()
        )

        Assertions.assertEquals(TaskOutcome.SUCCESS, runVerifyPom().task(":sub:verifyPom")?.outcome)
    }
}
