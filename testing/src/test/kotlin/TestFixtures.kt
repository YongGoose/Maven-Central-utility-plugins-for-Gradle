import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions

/**
 * Build-script fragments shared by the TestKit tests.
 *
 * Every test used to carry its own 40-line copy of the same `rootProjectPom` block, which buried
 * the one line that actually differed between them.
 */
object PomFixture {

    /** A POM block that satisfies every Maven Central requirement. */
    fun pomBlock(
        extensionName: String = "rootProjectPom",
        groupId: String = "io.github.yonggoose",
        artifactId: String = "organization-defaults",
        version: String = "1.0.0"
    ): String =
        """
        $extensionName {
            groupId = "$groupId"
            artifactId = "$artifactId"
            version = "$version"

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

    /**
     * `settings.gradle.kts` for a standalone build.
     *
     * Without one, Gradle walks up from the `@TempDir` looking for a settings file and would
     * silently join an enclosing build if the temp directory ever sat inside a project.
     */
    fun singleProjectSettings(name: String): String = "rootProject.name = \"$name\"\n"

    /** `settings.gradle.kts` for a `root` + `sub` build that resolves plugins from the test classpath. */
    fun multiModuleSettings(): String =
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
}

/**
 * Asserts that the build failed *because validation rejected the POM*, and that the report
 * mentions each of [expectedProblems].
 *
 * Plain `message.contains("Validation failed")`-style assertions are not enough on their own:
 * a script compilation error also produces an [UnexpectedBuildFailure], so a test can go green
 * for a reason that has nothing to do with the rule it claims to cover. This checks that
 * validation actually ran first, and prints the whole build output when it did not.
 */
fun assertValidationRejected(exception: UnexpectedBuildFailure, vararg expectedProblems: String) {
    val message = exception.message ?: ""

    Assertions.assertTrue(
        message.contains("Validation failed"),
        "the build failed before validation ran:\n$message"
    )
    expectedProblems.forEach { problem ->
        Assertions.assertTrue(
            message.contains(problem),
            "expected the report to mention \"$problem\":\n$message"
        )
    }
}
