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
        val projectPomExt = project.extensions.create("projectPom", ProjectPomExtension::class.java)

        open class EffectivePomExtension {
            var name: String? = null
            var url: String? = null
            var license: String? = null
            var developers: List<String> = emptyList()
        }

        val effectivePomExt = project.extensions.create("effectivePom", EffectivePomExtension::class.java)

        project.afterEvaluate {
            val orgDefaults = OrganizationDefaults(
                name = project.rootProject.extensions.findByType(OrganizationDefaultsExtension::class.java)?.name,
                url = project.rootProject.extensions.findByType(OrganizationDefaultsExtension::class.java)?.url,
                license = project.rootProject.extensions.findByType(OrganizationDefaultsExtension::class.java)?.license,
                developers = project.rootProject.extensions.findByType(OrganizationDefaultsExtension::class.java)?.developers
                    ?: emptyList()
            )

            val projectPom = OrganizationDefaults(
                name = projectPomExt.name,
                url = projectPomExt.url,
                license = projectPomExt.license,
                developers = projectPomExt.developers
            )

            val merged = orgDefaults.merge(projectPom)

            effectivePomExt.name = merged.name
            effectivePomExt.url = merged.url
            effectivePomExt.license = merged.license
            effectivePomExt.developers = merged.developers

            project.extensions.extraProperties.set("mergedDefaults", merged)
        }
    }
}