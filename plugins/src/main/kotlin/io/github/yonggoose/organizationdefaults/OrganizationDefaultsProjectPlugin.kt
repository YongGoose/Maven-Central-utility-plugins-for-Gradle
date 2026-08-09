package io.github.yonggoose.organizationdefaults

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Extension for specifying default POM metadata for Gradle projects.
 *
 * Registered twice by [OrganizationDefaultsProjectPlugin]: as `rootProjectPom` on the root
 * project (the organization-wide defaults) and as `projectPom` on every project (the per-module
 * overrides). The DSL itself lives in [AbstractPomMetadataExtension].
 */
open class PomDefaultsExtension : AbstractPomMetadataExtension()

/**
 * Gradle plugin for providing and merging organization-wide default POM metadata into projects.
 */
class OrganizationDefaultsProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val projectPomExt = project.extensions.create("projectPom", PomDefaultsExtension::class.java)

        if (project == project.rootProject) {
            project.extensions.create("rootProjectPom", PomDefaultsExtension::class.java)
        }

        project.afterEvaluate {
            val rootPomExt = project.rootProject.extensions.findByName(ROOT_EXTENSION_NAME) as? PomDefaultsExtension
            if (rootPomExt == null) {
                project.logger.warn(
                    "No '$ROOT_EXTENSION_NAME' extension found on the root project, so only the " +
                        "'projectPom' of '${project.path}' is used. Apply " +
                        "'io.github.yonggoose.maven.central.utility.plugin.project' to the root project " +
                        "to share organization-wide POM defaults."
                )
            }

            val orgDefaults = rootPomExt?.toOrganizationDefaults() ?: OrganizationDefaults()
            val merged = orgDefaults.merge(projectPomExt.toOrganizationDefaults())

            project.extensions.extraProperties.set(MERGED_DEFAULTS_PROPERTY, merged)
        }
    }

    companion object {
        /**
         * Name of the `ExtraProperties` entry holding the merged [OrganizationDefaults]
         * for a given project.
         */
        const val MERGED_DEFAULTS_PROPERTY: String = "mergedDefaults"

        private const val ROOT_EXTENSION_NAME = "rootProjectPom"
    }
}
