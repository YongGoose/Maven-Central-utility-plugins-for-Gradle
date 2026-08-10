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
        projectDir.resolve("settings.gradle.kts").toFile().writeText(PomFixture.multiModuleSettings())

        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                id("io.github.yonggoose.maven.central.utility.plugin.project")
            }

            ${PomFixture.pomBlock()}
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

    private fun runSubCheck() = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withArguments("sub:checkProjectArtifact", "--stacktrace")
        .withPluginClasspath()
        .forwardOutput()

    @Test
    fun `submodule overrides are validated, not the root project's metadata`() {
        writeMultiModuleProject(
            """
            projectPom {
                version = "2.0.0-SNAPSHOT"
            }
            """.trimIndent()
        )

        val exception = assertThrows<UnexpectedBuildFailure> { runSubCheck().build() }

        // Previously this validated the root's version (1.0.0) and reported success.
        assertValidationRejected(exception, "Invalid version")
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

        val result = runSubCheck().build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":sub:checkProjectArtifact")?.outcome)
    }

    @Test
    fun `a submodule can repair metadata the root project is missing`() {
        projectDir.resolve("settings.gradle.kts").toFile().writeText(PomFixture.multiModuleSettings())

        // The root only declares coordinates, so it would fail validation on its own.
        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                id("io.github.yonggoose.maven.central.utility.plugin.project")
            }

            rootProjectPom {
                groupId = "io.github.yonggoose"
                version = "1.0.0"
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

            ${PomFixture.pomBlock(extensionName = "projectPom", artifactId = "organization-defaults-sub")}
            """.trimIndent()
        )

        val result = runSubCheck().build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":sub:checkProjectArtifact")?.outcome)
    }
}
