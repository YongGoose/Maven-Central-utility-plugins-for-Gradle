import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * `checkProjectArtifact` under `--configuration-cache`.
 *
 * The task builds its whole report inside a `doLast` block that dereferences `Project` at
 * execution time — the merged metadata out of `extraProperties`, the `publishing` and `signing`
 * extensions, the `Sign` task list, another task's `state`, the logger. Gradle refuses to
 * serialize a task action holding a `Project`, so a consumer who turns the configuration cache on
 * cannot run the task at all.
 *
 * Two runs, not one: the first stores the entry, the second reuses it. A task that survives
 * storing can still break on reuse, and reuse is the run every subsequent build in a project does.
 *
 * Tracked in https://github.com/YongGoose/Maven-Central-utility-plugins-for-Gradle/issues/43.
 */
class ConfigurationCacheTest {

    @TempDir
    lateinit var projectDir: Path

    private fun runCheck() = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withArguments("checkProjectArtifact", "--configuration-cache", "--stacktrace")
        .withPluginClasspath()
        .forwardOutput()
        .build()

    @Test
    fun `the check task can be stored in and reused from the configuration cache`() {
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
            // skips the Sign tasks and the run reports SKIPPED. This test is about where the task
            // can run, not about what it concludes.
            signing {
                setRequired(false)
                sign(publishing.publications)
            }
            """.trimIndent()
        )

        val stored = runCheck()
        Assertions.assertEquals(TaskOutcome.SUCCESS, stored.task(":checkProjectArtifact")?.outcome)
        Assertions.assertTrue(
            stored.output.contains("Configuration cache entry stored"),
            "the first run did not store an entry:\n${stored.output}"
        )

        val reused = runCheck()
        Assertions.assertEquals(TaskOutcome.SUCCESS, reused.task(":checkProjectArtifact")?.outcome)
        Assertions.assertTrue(
            reused.output.contains("Configuration cache entry reused"),
            "the second run did not reuse the entry, so the task is only cacheable on paper:\n${reused.output}"
        )
    }
}
