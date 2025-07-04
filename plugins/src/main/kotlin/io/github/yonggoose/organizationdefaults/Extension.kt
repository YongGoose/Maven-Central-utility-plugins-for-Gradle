package io.github.yonggoose.organizationdefaults

import io.github.yonggoose.organizationdefaults.spec.DevelopersContainer
import io.github.yonggoose.organizationdefaults.spec.MailingListsContainer
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

open class OrganizationDefaultsExtension {
    var groupId: String? = null
    var artifactId: String? = null
    var version: String? = null

    var name: String? = null
    var description: String? = null
    var url: String? = null
    var inceptionYear: String? = null
    var license: String? = null

    var organization: Organization? = null

    private val developersContainer = DevelopersContainer()
    private val mailingListsContainer = MailingListsContainer()

    var developers: List<Developer> = emptyList()
        get() = developersContainer.getDevelopers()
        private set

    var mailingLists: List<MailingList> = emptyList()
        get() = mailingListsContainer.getMailingLists()
        private set

    var issueManagement: IssueManagement? = null

    var scm: Scm? = null

    fun developers(action: DevelopersContainer.() -> Unit) {
        developersContainer.action()
    }

    fun mailingLists(action: MailingListsContainer.() -> Unit) {
        mailingListsContainer.action()
    }
}

interface OrganizationDefaultsParameters : BuildServiceParameters {
    val groupId: Property<String>
    val artifactId: Property<String>
    val version: Property<String>

    val name: Property<String>
    val description: Property<String>
    val url: Property<String>
    val inceptionYear: Property<String>
    val license: Property<String>

    val organizationName: Property<String>
    val organizationUrl: Property<String>

    val developers: ListProperty<Developer>
    val mailingLists: ListProperty<MailingList>

    val issueManagementSystem: Property<String>
    val issueManagementUrl: Property<String>

    val scmConnection: Property<String>
    val scmDeveloperConnection: Property<String>
    val scmTag: Property<String>
    val scmUrl: Property<String>
}

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
            license = parameters.license.orNull

            organization = Organization(
                name = parameters.organizationName.orNull,
                url = parameters.organizationUrl.orNull
            )

            parameters.developers.orNull?.let { devs ->
                developers(action = {
                    devs.forEach { dev ->
                        developer {
                            id = dev.id
                            name = dev.name
                            email = dev.email
                            url = dev.url
                            organization = dev.organization
                            organizationUrl = dev.organizationUrl
                            timezone = dev.timezone
                        }
                    }
                })
            }

            parameters.mailingLists.orNull?.let { lists ->
                mailingLists(action = {
                    lists.forEach { list ->
                        mailingList {
                            name = list.name
                            subscribe = list.subscribe
                            unsubscribe = list.unsubscribe
                            post = list.post
                            archive = list.archive
                        }
                    }
                })
            }

            issueManagement = IssueManagement(
                system = parameters.issueManagementSystem.orNull,
                url = parameters.issueManagementUrl.orNull
            )

            scm = Scm(
                connection = parameters.scmConnection.orNull,
                developerConnection = parameters.scmDeveloperConnection.orNull,
                tag = parameters.scmTag.orNull,
                url = parameters.scmUrl.orNull
            )
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
            parameters.groupId.set(ext.groupId ?: "")
            parameters.artifactId.set(ext.artifactId ?: "")
            parameters.version.set(ext.version ?: "")

            parameters.name.set(ext.name ?: "")
            parameters.description.set(ext.description ?: "")
            parameters.url.set(ext.url ?: "")
            parameters.inceptionYear.set(ext.inceptionYear ?: "")
            parameters.license.set(ext.license ?: "")

            parameters.developers.set(ext.developers)
            parameters.mailingLists.set(ext.mailingLists)

            parameters.issueManagementSystem.set(ext.issueManagement?.system ?: "")
            parameters.issueManagementUrl.set(ext.issueManagement?.url ?: "")

            parameters.organizationName.set(ext.organization?.name ?: "")
            parameters.organizationUrl.set(ext.organization?.url ?: "")

            parameters.scmConnection.set(ext.scm?.connection ?: "")
            parameters.scmDeveloperConnection.set(ext.scm?.developerConnection ?: "")
            parameters.scmTag.set(ext.scm?.tag ?: "")
            parameters.scmUrl.set(ext.scm?.url ?: "")
        }
    }
}
