package io.github.yonggoose.organizationdefaults

import io.github.yonggoose.organizationdefaults.OrganizationDefaults
import org.gradle.api.Plugin
import org.gradle.api.Project

open class PomDefaultsExtension {
    var name: String? = null
    var url: String? = null
    var license: String? = null
    var developers: List<String> = emptyList()
}

class OrganizationDefaultsProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val projectPomExt = project.extensions.create("projectPom", PomDefaultsExtension::class.java)

        if (project == project.rootProject) {
            project.extensions.create("rootProjectPom", PomDefaultsExtension::class.java)
        }

        project.afterEvaluate {
            val organizationDefaultExtension = project.rootProject.extensions.findByName("rootProjectPom") as? PomDefaultsExtension
                ?: PomDefaultsExtension()

            val orgDefaults = OrganizationDefaults(
                name = organizationDefaultExtension.name,
                url = organizationDefaultExtension.url,
                license = organizationDefaultExtension.license,
                developers = organizationDefaultExtension.developers
            )

            val projectPom = OrganizationDefaults(
                name = projectPomExt.name,
                url = projectPomExt.url,
                license = projectPomExt.license,
                developers = projectPomExt.developers
            )

            val merged = orgDefaults.merge(projectPom)
            project.extensions.extraProperties.set("mergedDefaults", merged)
        }
    }
}