import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Wiring tests for `checkProjectArtifact`: that the task runs, reaches the validator, and
 * surfaces its report.
 *
 * The rules themselves are covered by the far cheaper unit tests in `:plugins`
 * ([io.github.yonggoose.organizationdefaults.MavenCentralMetadataValidatorTest] and friends),
 * so this class deliberately does not enumerate them again — each case here costs a Gradle build.
 *
 * Note there is no `@TestInstance(PER_CLASS)`: that made all of these share one `@TempDir`, so
 * files written by one test leaked into the next.
 */
class ArtifactCheckPluginTest {

    @TempDir
    lateinit var projectDir: Path

    private fun buildScript(content: String) {
        projectDir.resolve("settings.gradle.kts").toFile()
            .writeText(PomFixture.singleProjectSettings("artifact-check"))
        projectDir.resolve("build.gradle.kts").toFile().writeText(content)
    }

    private fun runCheck() = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withArguments("checkProjectArtifact", "--stacktrace")
        .withPluginClasspath()
        .forwardOutput()

    @Test
    fun `succeeds on a complete pom`() {
        projectDir.resolve("src/main/java/").toFile().mkdirs()
        projectDir.resolve("src/main/java/HelloWorld.java").toFile().writeText(
            """
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello, World");
                }
            }
            """.trimIndent()
        )

        buildScript(
            """
            plugins {
                java
                application
                signing
                id("com.vanniktech.maven.publish") version "0.34.0"
                id("io.github.yonggoose.maven.central.utility.plugin.check")
                id("io.github.yonggoose.maven.central.utility.plugin.project")
            }

            application {
                mainClass = "HelloWorld"
            }

            signing {
                setRequired(false)
            }

            ${PomFixture.pomBlock()}

            mavenPublishing {
                publishToMavenCentral()
            }
            """.trimIndent()
        )

        val result = runCheck().build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":checkProjectArtifact")?.outcome)
        // This build sets `setRequired(false)`, so no signature is inspected and the task must
        // say so rather than claim the signatures were verified.
        Assertions.assertTrue(
            result.output.contains("PGP signature verification was SKIPPED"),
            result.output
        )
    }

    @Test
    fun `lists every missing required field in one report`() {
        buildScript(
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

            rootProjectPom {
                groupId = "io.github.yonggoose"
                artifactId = "organization-defaults"
                version = "1.0.0"
            }
            """.trimIndent()
        )

        val exception = assertThrows<UnexpectedBuildFailure> { runCheck().build() }

        assertValidationRejected(
            exception,
            "Missing name",
            "Missing description",
            "Missing url",
            "Missing licenses",
            "Missing developers",
            "Missing scm"
        )
    }

    @Test
    fun `reports a snapshot version`() {
        buildScript(
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

            ${PomFixture.pomBlock(version = "1.0.0-SNAPSHOT")}
            """.trimIndent()
        )

        val exception = assertThrows<UnexpectedBuildFailure> { runCheck().build() }

        assertValidationRejected(exception, "Invalid version")
    }

    @Test
    fun `reports a missing maven-publish plugin`() {
        buildScript(
            """
            plugins {
                id("io.github.yonggoose.maven.central.utility.plugin.check")
                id("io.github.yonggoose.maven.central.utility.plugin.project")
            }

            ${PomFixture.pomBlock()}
            """.trimIndent()
        )

        val exception = assertThrows<UnexpectedBuildFailure> { runCheck().build() }

        assertValidationRejected(exception, "'maven-publish' plugin not found")
    }
}
