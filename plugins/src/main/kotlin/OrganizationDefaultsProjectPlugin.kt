import org.gradle.api.Plugin
import org.gradle.api.Project

open class ProjectPomExtension {
    var name: String? = null
    var url: String? = null
    var license: String? = null
    var developers: List<String> = emptyList()
}

class OrganizationDefaultsProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext = project.extensions.create("projectPom", ProjectPomExtension::class.java)

        project.afterEvaluate {
            // Try to get organization defaults from shared service first
            val orgDefaultsService = project.gradle.sharedServices.registrations
                .findByName("orgDefaults")?.service?.get() as? OrganizationDefaultsService

            // Create organization defaults object from service or extension
            val orgDefaults = if (orgDefaultsService != null) {
                val defaults = orgDefaultsService.getDefaults()
                OrganizationDefaults(
                    name = defaults.name,
                    url = defaults.url,
                    license = defaults.license,
                    developers = defaults.developers
                )
            } else {
                // Fallback to the old implementation
                OrganizationDefaults(
                    name = project.rootProject.extensions.findByType(OrganizationDefaultsExtension::class.java)?.name,
                    url = project.rootProject.extensions.findByType(OrganizationDefaultsExtension::class.java)?.url,
                    license = project.rootProject.extensions.findByType(OrganizationDefaultsExtension::class.java)?.license,
                    developers = project.rootProject.extensions.findByType(OrganizationDefaultsExtension::class.java)?.developers ?: emptyList()
                )
            }

            // Project-specific POM configuration
            val projectPom = OrganizationDefaults(
                name = ext.name,
                url = ext.url,
                license = ext.license,
                developers = ext.developers
            )

            // Merge organization defaults with project-specific settings
            val merged = orgDefaults.merge(projectPom)
            project.extensions.add("effectivePom", merged)
        }
    }
}