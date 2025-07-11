package unit

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ArtifactCheckPluginTest {

    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `checkProjectArtifact task should pass validation`() {
        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                id("io.github.yonggoose.organization-defaults-artifact-check-project")
                id("io.github.yonggoose.organization-defaults-project")
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
                        licenseType = "MIT"
                    }
                    license {
                        licenseType = "Apache-2.0"
                    }
                }
                
                developers {
                    developer {
                        id = "dev1"
                        name = "Developer1"
                        email = "dev1@example.com"
                        timezone = "UTC"
                        organization = "YongGoose"
                        organizationUrl = "https://yonggoose.github.io"
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

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("checkProjectArtifact")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkProjectArtifact")?.outcome)
    }

    @Test
    fun `checkSettingsArtifact task should fail validation`() {
        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                id("io.github.yonggoose.organization-defaults-artifact-check-project")
                id("io.github.yonggoose.organization-defaults-project")
            }
            
             rootProjectPom {
                groupId = "io.github.yonggoose"
                artifactId = "organization-defaults"
                version = "1.0.0"
                
                
                licenses {
                    license {
                        licenseType = "MIT"
                    }
                    license {
                        licenseType = "Apache-2.0"
                    }
                }
                
                developers {
                    developer {
                        id = "dev1"
                        name = "Developer1"
                        email = "dev1@example.com"
                        timezone = "UTC"
                    }
                }
            }
            """.trimIndent()
        )

        val exception = assertThrows<UnexpectedBuildFailure> {
            GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("checkProjectArtifact")
                .withPluginClasspath()
                .build()
        }
        assertTrue(exception.message?.contains("Validation failed") == true)
    }
}