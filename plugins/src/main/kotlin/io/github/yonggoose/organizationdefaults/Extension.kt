package io.github.yonggoose.organizationdefaults

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

open class OrganizationDefaultsExtension {
    var name: String? = null
    var url: String? = null
    var license: String? = null
    var developers: List<String> = emptyList()
}

interface OrganizationDefaultsParameters : BuildServiceParameters {
    val name: Property<String>
    val url: Property<String>
    val license: Property<String>
    val developers: ListProperty<String>
}

abstract class OrganizationDefaultsService : BuildService<OrganizationDefaultsParameters> {
    fun getDefaults(): OrganizationDefaultsExtension {
        return OrganizationDefaultsExtension().apply {
            name = parameters.name.orNull
            url = parameters.url.orNull
            license = parameters.license.orNull
            developers = parameters.developers.orNull ?: emptyList()
        }
    }
}

class OrganizationDefaultsSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val ext = settings.extensions.create("rootProjectSetting", OrganizationDefaultsExtension::class.java)

        settings.gradle.sharedServices.registerIfAbsent(
            "rootProjectSetting",
            OrganizationDefaultsService::class.java
        ) {
            parameters.name.set(ext.name ?: "")
            parameters.url.set(ext.url ?: "")
            parameters.license.set(ext.license ?: "")
            parameters.developers.set(ext.developers)
        }
    }
}