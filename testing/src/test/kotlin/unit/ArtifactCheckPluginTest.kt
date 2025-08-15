package unit

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArtifactCheckPluginTest {

    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `checkProjectArtifact task should pass validation`() {
        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                id("io.github.yonggoose.kotlin-pom-gradle-artifact-check-project")
                id("io.github.yonggoose.kotlin-pom-gradle-project")
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
        projectDir.resolve("src/main/java/Hello.java").toFile().writeText(
            """
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello, World");
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
    fun `checkProjectArtifact task should pass for vanniktech's gradle-maven-publish-plugin`() {
        val privateKeyResource = this::class.java.getResourceAsStream("/private.asc")
        val privateKeyFile = projectDir.resolve("private.asc").toFile()
        privateKeyResource.use { input ->
            privateKeyFile.outputStream().use { output ->
                input?.copyTo(output)
            }
        }

        ProcessBuilder(
            "gpg",
            "--homedir", File(projectDir.toFile(), "gnupg-home").absolutePath,
            "--import", privateKeyFile.absolutePath
        ).start().waitFor()

        println(privateKeyFile.absolutePath)

        projectDir.resolve("gradle.properties").toFile().writeText(
            """
            signing.gnupg.executable=gpg
            signing.gnupg.useLegacyGpg=true
            signing.gnupg.homeDir=gnupg-home
            signing.gnupg.optionsFile=gnupg-home/gpg.conf
            signing.gnupg.keyName=60FC9DADE1E31CBC3E0FB016565A412999781129
            signing.gnupg.passphrase=1234qwer
            """.trimIndent()
        )

        projectDir.resolve("src/main/java/").toFile().mkdirs();
        projectDir.resolve("src/main/java/HelloWorld.java").toFile().writeText(
            """
             public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello, World");
                }
            }
        """.trimIndent()
        )

        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                java
                application
                signing
                id("com.vanniktech.maven.publish") version "0.34.0"
                id("io.github.yonggoose.kotlin-pom-gradle-artifact-check-project")
                id("io.github.yonggoose.kotlin-pom-gradle-project")
            }
            
            application {
                mainClass = "HelloWorld" 
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
            
            mavenPublishing {
              publishToMavenCentral()
              signAllPublications()
            }
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("signMavenPublication", "--info")
            .withPluginClasspath()
            .build()

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("checkProjectArtifact", "--info")
            .withPluginClasspath()
            .build()

        val publicContent = File(projectDir.toFile(), "public.asc").readText()
        val privateContent = File(projectDir.toFile(), "private.asc").readText()
        assertTrue(publicContent.contains("BEGIN PGP PUBLIC KEY BLOCK"))
        assertTrue(privateContent.contains("BEGIN PGP PRIVATE KEY BLOCK"))

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkProjectArtifact")?.outcome)
    }

    @Test
    fun `checkSettingsArtifact task should fail validation`() {
        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                id("io.github.yonggoose.kotlin-pom-gradle-artifact-check-project")
                id("io.github.yonggoose.kotlin-pom-gradle-project")
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