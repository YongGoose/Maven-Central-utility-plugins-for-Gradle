package unit

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ExtensionTest {

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
            
            plugins {
                id("io.github.yonggoose.organization-defaults-setting")
            }
            
            rootProjectSetting {
                name = "MyCompany"
                url = "https://mycompany.com"
                license = "MIT"
                developers = listOf("Alice", "Bob")
            }

            include("sub")
            """.trimIndent()
        )

        val subDir = projectDir.resolve("sub").toFile().apply { mkdirs() }
        subDir.resolve("build.gradle.kts").writeText(
            """    
            import io.github.yonggoose.organizationdefaults.OrganizationDefaultsExtension
            import io.github.yonggoose.organizationdefaults.OrganizationDefaultsService
            
            tasks.register("verifyExtension") {
                doLast {
                    val service = gradle.sharedServices
                        .registrations
                        .named("rootProjectSetting")
                        .get()
                        .service
                        .get() as OrganizationDefaultsService
                    
                    val defaults = service.getDefaults()
                    
                    assertEquals("MyCompany", defaults.name)
                    assertEquals("https://mycompany.com", defaults.url)
                    assertEquals("MIT", defaults.license)
                    assertEquals(listOf("Alice", "Bob"), defaults.developers)
                }
            }
        """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("sub:verifyExtension", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":sub:verifyExtension")?.outcome)
    }
}
