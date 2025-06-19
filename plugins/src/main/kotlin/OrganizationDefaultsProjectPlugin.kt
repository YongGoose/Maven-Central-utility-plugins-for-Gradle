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

            val orgDefaults = OrganizationDefaults(
                name = project.rootProject.extensions.findByType(OrganizationDefaultsExtension::class.java)?.name,
                url = project.rootProject.extensions.findByType(OrganizationDefaultsExtension::class.java)?.url,
                license = project.rootProject.extensions.findByType(OrganizationDefaultsExtension::class.java)?.license,
                developers = project.rootProject.extensions.findByType(OrganizationDefaultsExtension::class.java)?.developers ?: emptyList()
            )
            val projectPom = OrganizationDefaults(
                name = ext.name,
                url = ext.url,
                license = ext.license,
                developers = ext.developers
            )
            val merged = orgDefaults.merge(projectPom)
            project.extensions.add("effectivePom", merged)
        }
    }
}