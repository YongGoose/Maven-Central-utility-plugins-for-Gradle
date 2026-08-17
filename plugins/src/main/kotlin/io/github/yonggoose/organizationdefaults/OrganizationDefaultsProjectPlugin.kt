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
 *
 * Three layers feed each project's `mergedDefaults`, each one overriding the one before it:
 *
 * 1. `rootProjectSetting { }` in `settings.gradle.kts`, from [OrganizationDefaultsSettingsPlugin];
 * 2. `rootProjectPom { }` on the root project;
 * 3. `projectPom { }` on the project itself.
 *
 * Layers 1 and 2 are both optional; a build that uses only one of them gets exactly the behaviour
 * it had when that was the only one available.
 */
class OrganizationDefaultsProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val projectPomExt = project.extensions.create("projectPom", PomDefaultsExtension::class.java)

        if (project == project.rootProject) {
            project.extensions.create("rootProjectPom", PomDefaultsExtension::class.java)
        }

        project.afterEvaluate {
            val rootExtension = project.rootProject.extensions.findByName(ROOT_EXTENSION_NAME)
            if (rootExtension != null && rootExtension !is PomDefaultsExtension) {
                // Distinct from "the plugin was never applied": it was, and left an extension this
                // build cannot read, which in practice means two versions of it on the build
                // classpath under different classloaders. Silently treating it as absent would
                // drop the organization defaults and surface as "Missing name / description / …".
                throw IllegalStateException(
                    "The root project's '$ROOT_EXTENSION_NAME' extension is a " +
                        "${rootExtension.javaClass.name}, not a ${PomDefaultsExtension::class.java.name}. " +
                        "Check for more than one version of " +
                        "'io.github.yonggoose.maven.central.utility.plugin.project' on the build classpath."
                )
            }

            val rootPomExt = rootExtension as? PomDefaultsExtension
            val settingsDefaults = resolveSettingsDefaults(project)

            if (rootPomExt == null && settingsDefaults == null) {
                project.logger.warn(
                    "No organization-wide POM defaults were found for '${project.path}', so only its own " +
                        "'projectPom' is used. Apply " +
                        "'io.github.yonggoose.maven.central.utility.plugin.project' to the root project for a " +
                        "'$ROOT_EXTENSION_NAME' block, or " +
                        "'io.github.yonggoose.maven.central.utility.plugin.setting' in settings.gradle.kts for a " +
                        "'${OrganizationDefaultsSettingsPlugin.EXTENSION_NAME}' block."
                )
            }

            val merged = (settingsDefaults ?: OrganizationDefaults())
                .merge(rootPomExt?.toOrganizationDefaults())
                .merge(projectPomExt.toOrganizationDefaults())

            project.extensions.extraProperties.set(MERGED_DEFAULTS_PROPERTY, merged)
        }
    }

    /**
     * What `rootProjectSetting { }` configured, or `null` when the settings plugin is not applied.
     *
     * Gradle exposes no path from a [Project] to the [org.gradle.api.initialization.Settings]
     * extensions, so [OrganizationDefaultsSettingsPlugin] puts the metadata into a shared build
     * service and this reads it back out. Being the base of the merge chain, it loses to both
     * `rootProjectPom` and `projectPom`.
     *
     * An applied-but-unconfigured settings plugin reads as `null` too. The two are the same thing
     * as far as the merge goes, and the caller's warning needs them to stay indistinguishable:
     * applying the settings plugin without writing a `rootProjectSetting { }` block must not
     * silence "no organization-wide POM defaults were found".
     */
    private fun resolveSettingsDefaults(project: Project): OrganizationDefaults? {
        val registration = project.gradle.sharedServices.registrations
            .findByName(OrganizationDefaultsSettingsPlugin.SERVICE_NAME)
            ?: return null

        val service = registration.service.get()
        if (service !is OrganizationDefaultsService) {
            // Same failure mode as the root-extension check above, one build phase earlier: the
            // settings plugin registered a service this project's classloader cannot see as ours.
            throw IllegalStateException(
                "The '${OrganizationDefaultsSettingsPlugin.SERVICE_NAME}' build service is a " +
                    "${service.javaClass.name}, not a ${OrganizationDefaultsService::class.java.name}. " +
                    "Check for more than one version of " +
                    "'io.github.yonggoose.maven.central.utility.plugin.setting' on the build classpath."
            )
        }

        return service.getDefaults().toOrganizationDefaults().takeIf { it != OrganizationDefaults() }
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
