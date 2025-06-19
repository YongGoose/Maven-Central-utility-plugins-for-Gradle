import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

open class OrganizationDefaultsExtension {
    var name: String? = null
    var url: String? = null
    var license: String? = null
    var developers: List<String> = emptyList()
}

class OrganizationDefaultsSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val ext = settings.extensions.create("organizationDefaults", OrganizationDefaultsExtension::class.java)
//        settings.gradle.sharedServices.registerIfAbsent("orgDefaults") {}
    }
}