import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArtifactCheckPluginTest {

    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `checkProjectArtifact task should pass`() {
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
                
                signing {
                    setRequired(false)
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
                            name = "MIT License"
                            url = "https://opensource.org/licenses/MIT"
                            distribution = "repo"
                            comments = "MIT License for open source projects"
                        }
                        license {
                            name = "Apache License 2.0"
                            url = "https://www.apache.org/licenses/LICENSE-2.0"
                            distribution = "repo"
                            comments = "Apache License for open source projects"
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
                }
        """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("checkProjectArtifact")
            .withPluginClasspath()
            .build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":checkProjectArtifact")?.outcome)
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
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                        distribution = "repo"
                        comments = "MIT License for open source projects"
                    }
                    license {
                        name = "Apache License 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                        distribution = "repo"
                        comments = "Apache License for open source projects"
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
        Assertions.assertTrue(exception.message?.contains("Validation failed") == true)
    }

    @Test
    fun `should fail when groupId is invalid`() {
        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
        plugins {
            id("io.github.yonggoose.kotlin-pom-gradle-artifact-check-project")
            id("io.github.yonggoose.kotlin-pom-gradle-project")
        }
        rootProjectPom {
            groupId = "invalidGroup"
            artifactId = "organization-defaults"
            version = "1.0.0"
            name = "Test"
            description = "desc"
            url = "https://example.org"
            developers { developer { name = "dev"; email = "dev@example.com"; organization = "Org"; organizationUrl = "https://org.com" } }
            scm { url = "url"; connection = "conn"; developerConnection = "devconn" }
        }
        """
        )
        val exception = assertThrows<UnexpectedBuildFailure> {
            GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("checkProjectArtifact")
                .withPluginClasspath()
                .build()
        }
        Assertions.assertTrue(exception.message?.contains("Invalid groupId") == true)
    }

    @Test
    fun `should fail when version ends with SNAPSHOT`() {
        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
        plugins {
            id("io.github.yonggoose.kotlin-pom-gradle-artifact-check-project")
            id("io.github.yonggoose.kotlin-pom-gradle-project")
        }
        rootProjectPom {
            groupId = "io.github.yonggoose"
            artifactId = "organization-defaults"
            version = "1.0.0-SNAPSHOT"
            name = "Test"
            description = "desc"
            url = "https://example.org"
            developers { developer { name = "dev"; email = "dev@example.com"; organization = "Org"; organizationUrl = "https://org.com" } }
            scm { url = "url"; connection = "conn"; developerConnection = "devconn" }
        }
        """
        )
        val exception = assertThrows<UnexpectedBuildFailure> {
            GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("checkProjectArtifact")
                .withPluginClasspath()
                .build()
        }
        Assertions.assertTrue(exception.message?.contains("Invalid version") == true)
    }
}