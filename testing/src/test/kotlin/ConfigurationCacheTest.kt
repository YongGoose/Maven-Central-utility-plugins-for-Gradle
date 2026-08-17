import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * `checkProjectArtifact` under `--configuration-cache`.
 *
 * The task builds its report from `Project` at execution time, and the freshness test at the heart
 * of it — whether a signature belongs to *this* build, answered by the producing task's
 * `state.didWork || state.upToDate` — has no configuration-time equivalent. So it declares itself
 * incompatible, and the point of these tests is that declaring it is not the same as failing:
 * Gradle turns the cache off for the build and runs the task, where before it refused with
 *
 * ```
 * cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject'
 * ```
 *
 * Two runs, because the second is where a stored entry would have been reused. Nothing may be
 * reused here, so the second run has to behave exactly like the first.
 *
 * Full compatibility stays open as
 * https://github.com/YongGoose/Maven-Central-utility-plugins-for-Gradle/issues/43.
 */
class ConfigurationCacheTest {

    /** The message Gradle refused with before the task declared itself incompatible. */
    private val serializationFailure = "cannot serialize object of type"

    @TempDir
    lateinit var projectDir: Path

    private fun runCheck(): BuildResult =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("checkProjectArtifact", "--configuration-cache", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

    @Test
    fun `the check task runs under the configuration cache instead of failing the build`() {
        projectDir.resolve("settings.gradle.kts").toFile()
            .writeText(PomFixture.singleProjectSettings("configuration-cache"))

        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                java
                `maven-publish`
                signing
                id("io.github.yonggoose.maven.central.utility.plugin.check")
                id("io.github.yonggoose.maven.central.utility.plugin.project")
            }

            ${PomFixture.pomBlock()}

            publishing {
                publications {
                    create<MavenPublication>("maven") {
                        from(components["java"])
                    }
                }
            }

            // No signatory anywhere, so Gradle's own `onlyIf { isRequired || signatory != null }`
            // skips the Sign tasks and the run reports SKIPPED. These tests are about where the
            // task can run, not about what it concludes.
            signing {
                setRequired(false)
                sign(publishing.publications)
            }
            """.trimIndent()
        )

        listOf("first", "second").forEach { which ->
            val result = runCheck()

            Assertions.assertEquals(
                TaskOutcome.SUCCESS,
                result.task(":checkProjectArtifact")?.outcome,
                "the $which run did not execute the task"
            )
            Assertions.assertFalse(
                result.output.contains(serializationFailure),
                "the $which run still failed to serialize the task:\n${result.output}"
            )
            // Running is not the same as reporting. An incompatible task that Gradle skipped
            // instead of executing would satisfy neither of these.
            Assertions.assertTrue(
                result.output.contains("PGP signature verification was SKIPPED"),
                "the $which run did not produce a verdict:\n${result.output}"
            )
        }
    }
}
