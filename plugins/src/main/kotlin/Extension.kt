import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

open class OrganizationDefaultsExtension {
    var name: String? = null
    var url: String? = null
    var license: String? = null
    var developers: List<String> = emptyList()
}

class OrganizationDefaultsSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val ext = settings.extensions.create("organizationDefaults", OrganizationDefaultsExtension::class.java)
        settings.gradle.sharedServices.registerIfAbsent(
            "orgDefaults",
            OrganizationDefaultsService::class.java
        ) { 
            parameters.extension.set(ext)
        }
    }
}

// Service to share organization defaults across projects
abstract class OrganizationDefaultsService : BuildService<OrganizationDefaultsService.Params> {
    interface Params : BuildServiceParameters {
        val extension: Property<OrganizationDefaultsExtension>
    }

    fun getDefaults(): OrganizationDefaultsExtension {
        return parameters.extension.get()
    }
}
