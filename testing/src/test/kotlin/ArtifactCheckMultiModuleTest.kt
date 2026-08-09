import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * `mergedDefaults` is written into every project the project plugin is applied to, so
 * `checkProjectArtifact` has to validate the metadata of the project it runs in. It used to read
 * the root project unconditionally, which silently ignored every submodule override.
 */
class ArtifactCheckMultiModuleTest {

    @TempDir
    lateinit var projectDir: Path

    private fun writeMultiModuleProject(subProjectPom: String) {
        projectDir.resolve("settings.gradle.kts").toFile().writeText(
            """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
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
                groupId = "io.github.yonggoose"
                artifactId = "organization-defaults"
                version = "1.0.0"

                name = "Test Organization"
                description = "Organization defaults plugin test"
                url = "https://example.org"

                licenses {
                    license {
                        name = "Apache-2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                        distribution = "repo"
                    }
                }

                developers {
                    developer {
                        id = "dev1"
                        name = "Developer1"
                        email = "dev1@example.com"
                    }
                }

                scm {
                    url = "https://github.com/YongGoose/organization-defaults"
                    connection = "scm:git:git@github.com:YongGoose/organization-defaults.git"
                    developerConnection = "scm:git:git@github.com:YongGoose/organization-defaults.git"
                }
            }
            """.trimIndent()
        )

        val subDir = projectDir.resolve("sub").toFile().apply { mkdirs() }
        subDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                java
                `maven-publish`
                signing
                id("io.github.yonggoose.maven.central.utility.plugin.check")
                id("io.github.yonggoose.maven.central.utility.plugin.project")
            }

            signing {
                setRequired(false)
            }

            $subProjectPom
            """.trimIndent()
        )
    }

    @Test
    fun `submodule overrides are validated, not the root project's metadata`() {
        writeMultiModuleProject(
            """
            projectPom {
                version = "2.0.0-SNAPSHOT"
            }
            """.trimIndent()
        )

        val exception = assertThrows<UnexpectedBuildFailure> {
            GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("sub:checkProjectArtifact", "--stacktrace")
                .withPluginClasspath()
                .forwardOutput()
                .build()
        }

        // Previously this validated the root's version (1.0.0) and reported success.
        Assertions.assertTrue(
            exception.message?.contains("Invalid version") == true,
            exception.message ?: "no failure message"
        )
    }

    @Test
    fun `submodule inherits valid root metadata when it overrides nothing`() {
        writeMultiModuleProject(
            """
            projectPom {
                artifactId = "organization-defaults-sub"
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("sub:checkProjectArtifact", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":sub:checkProjectArtifact")?.outcome)
    }
}
