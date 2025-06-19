import org.gradle.api.Project

object PomUtils {
    fun fromGradleProperties(project: Project): OrganizationDefaults {
        val props = project.properties
        return OrganizationDefaults(
            name = props["pom.name"] as? String,
            url = props["pom.url"] as? String,
            license = props["pom.license"] as? String,
            developers = (props["pom.developers"] as? String)?.split(",") ?: emptyList()
        )
    }
}