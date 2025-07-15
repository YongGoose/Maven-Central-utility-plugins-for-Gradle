package io.github.yonggoose.organizationdefaults

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A Gradle plugin that adds an artifact verification task to the project.
 * Validates the metadata of artifacts to be published according to Maven Central requirements.
 */
class ArtifactCheckPluginForProject : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("checkProjectArtifact") {
            group = "Verification"
            description =
                "Verifies that all artifacts staged for publishing are signed according to Maven Central requirements."

            doLast {
                val pom = project.rootProject.extensions.extraProperties.get("mergedDefaults") as? OrganizationDefaults
                    ?: throw IllegalStateException("mergedDefaults is not defined in the root project.")

                val errors = mutableListOf<String>()

                // GAV Coordinates validation
                val groupId = pom.groupId
                if (groupId == null || groupId.isBlank() || !groupId.matches(Regex("^[a-z]+(\\.[a-z][a-z0-9]*)+$"))) {
                    errors.add("Invalid groupId: Must be in reverse domain name format or not be null or blank.")
                }

                if (pom.artifactId.isNullOrBlank()) {
                    errors.add("Invalid artifactId: Must be a unique component name.")
                }

                val version = pom.version
                if (version == null || (version.isBlank() || version.endsWith("-SNAPSHOT"))) {
                    errors.add("Invalid version: The version must not be null, blank, or end with '-SNAPSHOT'.")
                }

                // Project Information validation
                if (pom.name.isNullOrBlank()) {
                    errors.add("Invalid name: Project name is required.")
                }
                if (pom.description.isNullOrBlank()) {
                    errors.add("Invalid description: Project description is required.")
                }
                if (pom.url.isNullOrBlank()) {
                    errors.add("Invalid url: Project URL is required.")
                }

                // License validation
                pom.licenses.forEach { license ->
                    if (license.licenseType.isNullOrBlank()) {
                        errors.add("Invalid license: License name is required.")
                    }
                }

                // Developer Info validation
                pom.developers.forEach { developer ->
                    if (developer.name.isNullOrBlank()) {
                        errors.add("Invalid developer: Developer name is required.")
                    }
                    if (developer.email.isNullOrBlank()) {
                        errors.add("Invalid developer: Developer email is required.")
                    }
                    if (developer.organization.isNullOrBlank()) {
                        errors.add("Invalid developer: Organization is required.")
                    }
                    if (developer.organizationUrl.isNullOrBlank()) {
                        errors.add("Invalid developer: Organization URL is required.")
                    }
                }

                // SCM Information validation
                pom.scm?.let { scm ->
                    if (scm.connection.isNullOrBlank()) {
                        errors.add("Invalid SCM: Read-only connection is required.")
                    }
                    if (scm.developerConnection.isNullOrBlank()) {
                        errors.add("Invalid SCM: Read/write connection is required.")
                    }
                    if (scm.url.isNullOrBlank()) {
                        errors.add("Invalid SCM: Web interface URL is required.")
                    }
                }

                // Throw exception if there are errors
                if (errors.isNotEmpty()) {
                    throw IllegalArgumentException("Validation failed:\n${errors.joinToString("\n")}")
                }

                project.logger.lifecycle("ArtifactCheckPlugin: All validations passed successfully.")
            }
        }
    }
}