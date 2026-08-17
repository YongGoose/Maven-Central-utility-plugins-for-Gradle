package io.github.yonggoose.organizationdefaults

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Extension for specifying organization-wide default metadata for Gradle projects.
 *
 * Registered as `rootProjectSetting` by [OrganizationDefaultsSettingsPlugin]. The DSL itself
 * lives in [AbstractPomMetadataExtension].
 */
open class OrganizationDefaultsExtension : AbstractPomMetadataExtension()

/**
 * Parameters for the OrganizationDefaults build service.
 */
interface OrganizationDefaultsParameters : BuildServiceParameters {
    val groupId: Property<String>
    val artifactId: Property<String>
    val version: Property<String>

    val name: Property<String>
    val description: Property<String>
    val url: Property<String>
    val inceptionYear: Property<String>

    val licenses: ListProperty<License>

    val developers: ListProperty<Developer>

    val mailingLists: ListProperty<MailingList>

    val organization: Property<Organization>

    val issueManagement: Property<IssueManagement>

    val scm: Property<Scm>
}

/**
 * Build service that provides organization default metadata to Gradle builds.
 */
abstract class OrganizationDefaultsService : BuildService<OrganizationDefaultsParameters> {
    fun getDefaults(): OrganizationDefaultsExtension {
        return OrganizationDefaultsExtension().apply {
            groupId = parameters.groupId.orNull
            artifactId = parameters.artifactId.orNull
            version = parameters.version.orNull

            name = parameters.name.orNull
            description = parameters.description.orNull
            url = parameters.url.orNull
            inceptionYear = parameters.inceptionYear.orNull

            parameters.licenses.orNull?.let { configuredLicenses ->
                licenses {
                    configuredLicenses.forEach { configured ->
                        license {
                            name = configured.name
                            url = configured.url
                            distribution = configured.distribution
                            comments = configured.comments
                        }
                    }
                }
            }

            parameters.developers.orNull?.let { configuredDevelopers ->
                developers {
                    configuredDevelopers.forEach { configured ->
                        developer {
                            id = configured.id
                            name = configured.name
                            email = configured.email
                            url = configured.url
                            organization = configured.organization
                            organizationUrl = configured.organizationUrl
                            timezone = configured.timezone
                        }
                    }
                }
            }

            parameters.mailingLists.orNull?.let { configuredMailingLists ->
                mailingLists {
                    configuredMailingLists.forEach { configured ->
                        mailingList {
                            name = configured.name
                            subscribe = configured.subscribe
                            unsubscribe = configured.unsubscribe
                            post = configured.post
                            archive = configured.archive
                        }
                    }
                }
            }

            parameters.organization.orNull?.let { configured ->
                organization {
                    organization {
                        name = configured.name
                        url = configured.url
                    }
                }
            }

            parameters.issueManagement.orNull?.let { configured ->
                issueManagement {
                    issueManagement {
                        system = configured.system
                        url = configured.url
                    }
                }
            }

            parameters.scm.orNull?.let { configured ->
                scm {
                    scm {
                        connection = configured.connection
                        developerConnection = configured.developerConnection
                        url = configured.url
                    }
                }
            }
        }
    }
}

/**
 * Gradle settings plugin for registering and configuring organization default metadata.
 *
 * A settings extension cannot be reached from a [org.gradle.api.Project], so what
 * `rootProjectSetting { }` configures is handed to [OrganizationDefaultsProjectPlugin] through the
 * shared build service registered here. That plugin is the only consumer; without it applied to a
 * project, configuring `rootProjectSetting` has no effect on that project's `mergedDefaults`.
 */
class OrganizationDefaultsSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val ext = settings.extensions.create(EXTENSION_NAME, OrganizationDefaultsExtension::class.java)

        settings.gradle.sharedServices.registerIfAbsent(
            SERVICE_NAME,
            OrganizationDefaultsService::class.java
        ) {
            // A `provider {}` lambda that returns null leaves the property unset, which keeps
            // "never configured" distinguishable from "configured to an empty string".
            parameters.groupId.set(settings.providers.provider<String> { ext.groupId })
            parameters.artifactId.set(settings.providers.provider<String> { ext.artifactId })
            parameters.version.set(settings.providers.provider<String> { ext.version })

            parameters.name.set(settings.providers.provider<String> { ext.name })
            parameters.description.set(settings.providers.provider<String> { ext.description })
            parameters.url.set(settings.providers.provider<String> { ext.url })
            parameters.inceptionYear.set(settings.providers.provider<String> { ext.inceptionYear })

            parameters.licenses.set(settings.providers.provider<List<License>> { ext.licenses })
            parameters.developers.set(settings.providers.provider<List<Developer>> { ext.developers })
            parameters.mailingLists.set(settings.providers.provider<List<MailingList>> { ext.mailingLists })

            parameters.organization.set(settings.providers.provider<Organization> { ext.organization })
            parameters.issueManagement.set(settings.providers.provider<IssueManagement> { ext.issueManagement })
            parameters.scm.set(settings.providers.provider<Scm> { ext.scm })
        }
    }

    companion object {
        /** Name of the settings extension: the `rootProjectSetting { }` block. */
        const val EXTENSION_NAME: String = "rootProjectSetting"

        /**
         * Name of the build service [OrganizationDefaultsProjectPlugin] looks up.
         *
         * Build service names share one namespace across the whole build tree, so this is
         * qualified rather than reusing [EXTENSION_NAME]: an unqualified `rootProjectSetting` is
         * a name another plugin could plausibly register, and this plugin would then greet that
         * build with a type error naming a plugin it never applied.
         */
        const val SERVICE_NAME: String = "io.github.yonggoose.organizationdefaults.rootProjectSetting"
    }
}
