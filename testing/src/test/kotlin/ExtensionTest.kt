import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions
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
                id("io.github.yonggoose.maven.central.utility.plugin.setting")
            }
            
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
                        name = "MIT License"
                        url = "https://opensource.org/license/mit/"
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
                
                organization {
                    name = "YongGoose"
                    url = "https://github.com/YongGoose"
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
                        timezone = "UTC"
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
                
                issueManagement {
                    system = "GitHub"
                    url = "https://github.com/YongGoose/organization-defaults/issues"
                }
                
                scm {
                    url = "https://github.com/YongGoose/organization-defaults"
                    connection = "scm:git:git@github.com:YongGoose/organization-defaults.git"
                    developerConnection = "scm:git:git@github.com:YongGoose/organization-defaults.git"
                }
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
                    
                    val pom = service.getDefaults()

                    check(pom.groupId == "io.github.yonggoose")
                    check(pom.artifactId == "organization-defaults")
                    check(pom.version == "1.0.0")
                    
                    check(pom.name == "Test Organization")
                    check(pom.description == "Organization defaults plugin test")
                    check(pom.url == "https://example.org")
                    check(pom.inceptionYear == "2023")
                    
                    check(pom.licenses.size == 2)
                    check(pom.licenses[0].name == "MIT License")
                    check(pom.licenses[0].url == "https://opensource.org/license/mit/")
                    check(pom.licenses[0].distribution == "repo")
                    check(pom.licenses[0].comments == "MIT License for open source projects")
                    check(pom.licenses[1].name == "Apache License 2.0")
                    check(pom.licenses[1].url == "https://www.apache.org/licenses/LICENSE-2.0")
                    check(pom.licenses[1].distribution == "repo")
                    check(pom.licenses[1].comments == "Apache License for open source projects")

                    check(pom.organization?.name == "YongGoose")
                    check(pom.organization?.url == "https://github.com/YongGoose")
                    
                    check(pom.developers.size == 2)
                    check(pom.developers[0].id == "dev1")
                    check(pom.developers[0].name == "Developer1")
                    check(pom.developers[0].email == "dev1@example.com")
                    check(pom.developers[1].id == "dev2")
                    check(pom.developers[1].name == "Developer2")
                    check(pom.developers[1].email == "dev2@example.com")

                    check(pom.mailingLists.size == 1)
                    check(pom.mailingLists[0].name == "Developers")
                    check(pom.mailingLists[0].subscribe == "dev-subscribe@example.org")
                    check(pom.mailingLists[0].unsubscribe == "dev-unsubscribe@example.org")
                    check(pom.mailingLists[0].post == "dev@example.org")
                    check(pom.mailingLists[0].archive == "https://example.org/archive")

                    check(pom.issueManagement?.system == "GitHub")
                    check(pom.issueManagement?.url == "https://github.com/YongGoose/organization-defaults/issues")

                    check(pom.scm?.url == "https://github.com/YongGoose/organization-defaults")
                    check(pom.scm?.connection == "scm:git:git@github.com:YongGoose/organization-defaults.git")
                    check(pom.scm?.developerConnection == "scm:git:git@github.com:YongGoose/organization-defaults.git")
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

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":sub:verifyExtension")?.outcome)
    }
}