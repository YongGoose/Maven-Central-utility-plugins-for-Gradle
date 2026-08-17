import org.gradle.testkit.runner.BuildResult
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

    /** The warning the project plugin logs when no level above `projectPom` supplied anything. */
    private val noDefaultsWarning = "No organization-wide POM defaults were found"

    @TempDir
    lateinit var projectDir: Path

    private fun writeSettings(body: String) {
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

            $body

            rootProject.name = "root"
            include("sub")
            """.trimIndent()
        )
    }

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

    private fun runVerifyPom(): BuildResult =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("sub:verifyPom", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

    /**
     * Every POM element the DSL offers, configured in settings and asserted out of `mergedDefaults`.
     *
     * The breadth is deliberate. `PomDefaultsService.getDefaults()` rebuilds the metadata
     * by replaying it through the DSL — `organization { organization { … } }`, one nested block per
     * container — and that code only became load-bearing when this PR started reading the service.
     * A field dropped in there would otherwise disappear from every published POM silently.
     */
    @Test
    fun `metadata configured in settings reaches a submodule's merged defaults`() {
        writeSettings(
            """
            rootProjectSetting {
                groupId = "io.github.yonggoose"
                artifactId = "organization-defaults"
                version = "1.0.0"

                name = "Test Organization"
                description = "Organization defaults plugin test"
                url = "https://example.org"
                inceptionYear = "2023"

                licenses {
                    license {
                        name = "MIT"
                        url = "https://opensource.org/license/mit/"
                        distribution = "repo"
                        comments = "MIT License for open source projects"
                    }
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
                        timezone = "UTC"
                    }
                    developer {
                        id = "dev2"
                        name = "Developer2"
                        email = "dev2@example.com"
                    }
                }

                mailingLists {
                    mailingList {
                        name = "Developers"
                        subscribe = "dev-subscribe@example.org"
                        unsubscribe = "dev-unsubscribe@example.org"
                        post = "dev@example.org"
                        archive = "https://example.org/archive"
                    }
                }

                organization {
                    name = "YongGoose"
                    url = "https://github.com/YongGoose"
                }

                issueManagement {
                    system = "GitHub"
                    url = "https://example.org/issues"
                }

                scm {
                    url = "https://github.com/YongGoose/organization-defaults"
                    connection = "scm:git:git@github.com:YongGoose/organization-defaults.git"
                    developerConnection = "scm:git:git@github.com:YongGoose/organization-defaults.git"
                }
            }
            """.trimIndent()
        )

        // No `rootProjectPom { }` here on purpose: settings has to be able to carry a build on its
        // own, otherwise the settings plugin is just a second spelling of the root project's block.
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
                    check(pom.inceptionYear == "2023") { "inceptionYear was ${'$'}{pom.inceptionYear}" }

                    check(pom.licenses.size == 2) { "licenses were ${'$'}{pom.licenses}" }
                    check(pom.licenses[0].name == "MIT") { "licenses[0] was ${'$'}{pom.licenses[0]}" }
                    check(pom.licenses[0].url == "https://opensource.org/license/mit/")
                    check(pom.licenses[0].distribution == "repo")
                    check(pom.licenses[0].comments == "MIT License for open source projects")
                    check(pom.licenses[1].name == "Apache-2.0") { "licenses[1] was ${'$'}{pom.licenses[1]}" }
                    check(pom.licenses[1].url == "https://www.apache.org/licenses/LICENSE-2.0")

                    check(pom.developers.size == 2) { "developers were ${'$'}{pom.developers}" }
                    check(pom.developers[0].id == "dev1")
                    check(pom.developers[0].name == "Developer1")
                    check(pom.developers[0].email == "dev1@example.com")
                    check(pom.developers[0].timezone == "UTC")
                    check(pom.developers[1].id == "dev2")
                    check(pom.developers[1].email == "dev2@example.com")

                    check(pom.mailingLists.size == 1) { "mailingLists were ${'$'}{pom.mailingLists}" }
                    check(pom.mailingLists[0].name == "Developers")
                    check(pom.mailingLists[0].subscribe == "dev-subscribe@example.org")
                    check(pom.mailingLists[0].unsubscribe == "dev-unsubscribe@example.org")
                    check(pom.mailingLists[0].post == "dev@example.org")
                    check(pom.mailingLists[0].archive == "https://example.org/archive")

                    check(pom.organization?.name == "YongGoose") { "organization was ${'$'}{pom.organization}" }
                    check(pom.organization?.url == "https://github.com/YongGoose")

                    check(pom.issueManagement?.system == "GitHub") { "issueManagement was ${'$'}{pom.issueManagement}" }
                    check(pom.issueManagement?.url == "https://example.org/issues")

                    check(pom.scm?.url == "https://github.com/YongGoose/organization-defaults") {
                        "scm was ${'$'}{pom.scm}"
                    }
                    check(pom.scm?.connection == "scm:git:git@github.com:YongGoose/organization-defaults.git")
                    check(
                        pom.scm?.developerConnection == "scm:git:git@github.com:YongGoose/organization-defaults.git"
                    )
                }
            }
            """.trimIndent()
        )

        Assertions.assertEquals(TaskOutcome.SUCCESS, runVerifyPom().task(":sub:verifyPom")?.outcome)
    }

    /**
     * The precedence the three levels are documented to have, in one build:
     * `rootProjectSetting` < `rootProjectPom` < `projectPom`.
     *
     * Every field below is set by a different subset of the three, so a merge that got the order
     * wrong — or that dropped a level — lands on a different value for at least one of them.
     */
    @Test
    fun `each layer overrides the one before it`() {
        writeSettings(
            """
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

    /**
     * The build shape the README recommends the settings plugin for: a root project that publishes
     * nothing and does not apply the project plugin at all, so there is no `rootProjectPom`
     * extension anywhere.
     *
     * This is the branch the wiring changed — `rootProjectPom` absent, settings present — and the
     * one where the "no organization-wide defaults" warning would be actively wrong.
     */
    @Test
    fun `settings alone carries a build whose root project does not apply the project plugin`() {
        writeSettings(
            """
            rootProjectSetting {
                groupId = "io.github.yonggoose"
                artifactId = "aggregated"
                version = "1.0.0"
                name = "Aggregated"
            }
            """.trimIndent()
        )

        writeSubProject(
            """
            tasks.register("verifyPom") {
                doLast {
                    val pom = project.extensions.extraProperties.get("mergedDefaults") as OrganizationDefaults

                    check(pom.groupId == "io.github.yonggoose") { "groupId was ${'$'}{pom.groupId}" }
                    check(pom.artifactId == "aggregated") { "artifactId was ${'$'}{pom.artifactId}" }
                    check(pom.version == "1.0.0") { "version was ${'$'}{pom.version}" }
                    check(pom.name == "Aggregated") { "name was ${'$'}{pom.name}" }
                }
            }
            """.trimIndent()
        )

        val result = runVerifyPom()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":sub:verifyPom")?.outcome)
        Assertions.assertFalse(
            result.output.contains(noDefaultsWarning),
            "settings supplied the defaults, so the build should not have been told it had none"
        )
    }

    /**
     * The other half of the previous test: applying the settings plugin is not the same as
     * configuring it.
     *
     * An applied-but-empty `rootProjectSetting` used to be enough to satisfy the "is there a level
     * above `projectPom`" test, which would silence the warning for exactly the build that needs
     * it — a shared convention `settings.gradle.kts` applied across repositories, one of which
     * never fills the block in.
     */
    @Test
    fun `applying the settings plugin without configuring it still warns`() {
        writeSettings("")

        writeSubProject(
            """
            tasks.register("verifyPom") {
                doLast {
                    check(project.extensions.extraProperties.has("mergedDefaults"))
                }
            }
            """.trimIndent()
        )

        Assertions.assertTrue(
            runVerifyPom().output.contains(noDefaultsWarning),
            "an empty 'rootProjectSetting' left the build with no defaults and no warning about it"
        )
    }
}
