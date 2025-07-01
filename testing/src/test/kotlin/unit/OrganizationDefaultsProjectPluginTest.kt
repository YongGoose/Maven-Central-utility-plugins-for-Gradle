package unit

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class OrganizationDefaultsFunctionalTest {

    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `plugin propagates defaults from root to sub-module`() {
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
                id("io.github.yonggoose.organization-defaults-project")
             }
                
             rootProjectPom {
                name       = "Test Organization"
                url        = "https://example.org"
                license    = "MIT"
                developers = listOf("Developer1", "Developer2")
            }
            """.trimIndent()
        )

        val subDir = projectDir.resolve("sub").toFile().apply { mkdirs() }
        subDir.resolve("build.gradle.kts").writeText(
            """
            import io.github.yonggoose.organizationdefaults.OrganizationDefaults
            
            plugins {
                id("io.github.yonggoose.organization-defaults-project")
            }

            tasks.register("verifyPom") {
                doLast {
                    if (!project.extensions.extraProperties.has("mergedDefaults")) {
                        throw GradleException("there is no merged defaults")
                    }

                    val pom = project.extensions.extraProperties.get("mergedDefaults") as OrganizationDefaults
                    check(pom.name == "Test Organization")
                    check(pom.url == "https://example.org")
                    check(pom.license == "MIT")
                    check(pom.developers == listOf("Developer1", "Developer2"))
                }
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("sub:verifyPom", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":sub:verifyPom")?.outcome)
    }

    @Test
    fun `verify submodule correctly overrides root settings`() {
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
                id("io.github.yonggoose.organization-defaults-project")
             }
                
             rootProjectPom {
                name       = "Test Organization"
                url        = "https://example.org"
                license    = "MIT"
                developers = listOf("Developer1", "Developer2")
            }
            """.trimIndent()
        )

        val subDir = projectDir.resolve("sub").toFile().apply { mkdirs() }
        subDir.resolve("build.gradle.kts").writeText(
            """
            import io.github.yonggoose.organizationdefaults.OrganizationDefaults

            projectPom {
                name = "Subproject"
                url = "https://www.google.com/"
            }
            
            plugins {
                id("io.github.yonggoose.organization-defaults-project")
            }

            tasks.register("verifyPom") {
                doLast {
                    if (!project.extensions.extraProperties.has("mergedDefaults")) {
                        throw GradleException("there is no merged defaults")
                    }

                    val pom = project.extensions.extraProperties.get("mergedDefaults") as OrganizationDefaults
                    check(pom.name == "Subproject")
                    check(pom.url == "https://www.google.com/")
                    check(pom.license == "MIT")
                    check(pom.developers == listOf("Developer1", "Developer2"))
                }
            }
            """.trimIndent()
        )


        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("sub:verifyPom", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":sub:verifyPom")?.outcome)
    }
}
