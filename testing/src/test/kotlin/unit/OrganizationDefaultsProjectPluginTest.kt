package unit

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class OrganizationDefaultsProjectPluginTest {

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
                id("io.github.yonggoose.kotlin-pom-gradle-project")
             }

             rootProjectPom {
                groupId = "io.github.yonggoose"
                artifactId = "organization-defaults"
                version = "1.0.0"
                
                name = "Test Organization"
                description = "Organization defaults plugin test"
                url = "https://example.org"
                inceptionYear = "2023"
                
                licenses {
                    license {
                        licenseType = "MIT"
                    }
                    license {
                        licenseType = "Apache-2.0"
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
            """.trimIndent()
        )

        val subDir = projectDir.resolve("sub").toFile().apply { mkdirs() }
        subDir.resolve("build.gradle.kts").writeText(
            """
            import io.github.yonggoose.organizationdefaults.OrganizationDefaults

            plugins {
                id("io.github.yonggoose.kotlin-pom-gradle-project")
            }

            tasks.register("verifyPom") {
                doLast {
                    if (!project.extensions.extraProperties.has("mergedDefaults")) {
                        throw GradleException("there is no merged defaults")
                    }

                    val pom = project.extensions.extraProperties.get("mergedDefaults") as OrganizationDefaults
                    check(pom.groupId == "io.github.yonggoose")
                    check(pom.artifactId == "organization-defaults")
                    check(pom.version == "1.0.0")
                    
                    check(pom.name == "Test Organization")
                    check(pom.description == "Organization defaults plugin test")
                    check(pom.url == "https://example.org")
                    check(pom.inceptionYear == "2023")
                    
                    check(pom.licenses.size == 2)
                    check(pom.licenses[0].licenseType == "MIT")
                    check(pom.licenses[1].licenseType == "Apache-2.0")

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
                id("io.github.yonggoose.kotlin-pom-gradle-project")
             }

             rootProjectPom {
                groupId = "io.github.yonggoose"
                artifactId = "organization-defaults"
                version = "1.0.0"
                
                name = "Test Organization"
                description = "Organization defaults plugin test"
                url = "https://example.org"
                inceptionYear = "2023"
                
                licenses {
                    license {
                        licenseType = "MIT"
                    }
                    license {
                        licenseType = "Apache-2.0"
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
            """.trimIndent()
        )

        val subDir = projectDir.resolve("sub").toFile().apply { mkdirs() }
        subDir.resolve("build.gradle.kts").writeText(
            """
            import io.github.yonggoose.organizationdefaults.OrganizationDefaults

            plugins {
                id("io.github.yonggoose.kotlin-pom-gradle-project")
            }
            
            projectPom {
                groupId = "io.github.yonggoose.sub"
                artifactId = "organization-defaults-sub"
                version = "2.0.0"
                
                name = "Subproject"
                description = "Subproject description"
                url = "https://www.google.com/"
                inceptionYear = "2024"
                
                licenses {
                    license {
                        licenseType = "MIT"
                    }
                }
                
                organization {
                    name = "SubOrg"
                    url = "https://sub.example.org"
                }
                
                issueManagement {
                    system = "JIRA"
                    url = "https://jira.example.org"
                }
                
                scm {
                    url = "https://gitlab.com/project"
                    connection = "scm:git:git@gitlab.com:project.git"
                    developerConnection = "scm:git:git@gitlab.com:project.git"
                }
            }

            tasks.register("verifyPom") {
                doLast {
                    if (!project.extensions.extraProperties.has("mergedDefaults")) {
                        throw GradleException("there is no merged defaults")
                    }

                    val pom = project.extensions.extraProperties.get("mergedDefaults") as OrganizationDefaults
                    check(pom.groupId == "io.github.yonggoose.sub")
                    check(pom.artifactId == "organization-defaults-sub")
                    check(pom.version == "2.0.0")
                    
                    check(pom.name == "Subproject")
                    check(pom.description == "Subproject description")
                    check(pom.url == "https://www.google.com/")
                    check(pom.inceptionYear == "2024")
                    
                    check(pom.licenses.size == 1)
                    check(pom.licenses[0].licenseType == "MIT")
                    
                    check(pom.organization?.name == "SubOrg")
                    check(pom.organization?.url == "https://sub.example.org")
                    
                    check(pom.developers.size == 2)
                    check(pom.developers[0].id == "dev1")
                    check(pom.developers[0].name == "Developer1")
                    check(pom.developers[0].email == "dev1@example.com")
                    check(pom.developers[1].id == "dev2")
                    check(pom.developers[1].name == "Developer2")
                    check(pom.developers[1].email == "dev2@example.com")
                    
                    check(pom.mailingLists.size == 1)
                    check(pom.mailingLists[0].name == "Developers")
                    
                    check(pom.issueManagement?.system == "JIRA")
                    check(pom.issueManagement?.url == "https://jira.example.org")
                    
                    check(pom.scm?.url == "https://gitlab.com/project")
                    check(pom.scm?.connection == "scm:git:git@gitlab.com:project.git")
                    check(pom.scm?.developerConnection == "scm:git:git@gitlab.com:project.git")
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