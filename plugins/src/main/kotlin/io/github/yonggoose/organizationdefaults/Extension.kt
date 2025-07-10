package io.github.yonggoose.organizationdefaults

import io.github.yonggoose.organizationdefaults.container.DevelopersContainer
import io.github.yonggoose.organizationdefaults.container.IssueManagementContainer
import io.github.yonggoose.organizationdefaults.container.LicenseContainer
import io.github.yonggoose.organizationdefaults.container.MailingListsContainer
import io.github.yonggoose.organizationdefaults.container.OrganizationContainer
import io.github.yonggoose.organizationdefaults.container.ScmContainer
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

    private val licenseContainer = LicenseContainer()
    private val developersContainer = DevelopersContainer()
    private val mailingListsContainer = MailingListsContainer()
    private val issueManageMentContainer = IssueManagementContainer()
    private val organizationContainer = OrganizationContainer()
    private val scmContainer = ScmContainer()

    var licenses: List<License> = emptyList()
        get() = licenseContainer.getLicenses()
        private set

    var developers: List<Developer> = emptyList()
        get() = developersContainer.getDevelopers()
        private set

    var mailingLists: List<MailingList> = emptyList()
        get() = mailingListsContainer.getMailingLists()
        private set

    var issueManagement: IssueManagement? = null
        get() = issueManageMentContainer.getIssueManagement()
        private set

    var organization: Organization? = null
        get() = organizationContainer.getOrganization()
        private set

    var scm: Scm? = null
        get() = scmContainer.getScm()
        private set

    fun licenses(action: LicenseContainer.() -> Unit) {
        licenseContainer.action()
    }

    fun developers(action: DevelopersContainer.() -> Unit) {
        developersContainer.action()
    }

    fun mailingLists(action: MailingListsContainer.() -> Unit) {
        mailingListsContainer.action()
    }

    fun issueManagement(action: IssueManagementContainer.() -> Unit) {
        issueManageMentContainer.action()
    }

    fun organization(action: OrganizationContainer.() -> Unit) {
        organizationContainer.action()
    }

    fun scm(action: ScmContainer.() -> Unit) {
        scmContainer.action()
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

    val licenses: ListProperty<License>

    val developers: ListProperty<Developer>

    val mailingLists: ListProperty<MailingList>

    val organization: Property<Organization>

    val issueManagement: Property<IssueManagement>

    val scm: Property<Scm>
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

            parameters.licenses.orNull?.let { licenses ->
                licenses(action = {
                    licenses.forEach { license ->
                        license {
                            licenseType = license.licenseType
                        }
                    }
                })
            }

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

            parameters.organization.orNull?.let { org ->
                organization(
                    action = {
                        organization {
                            name = org.name
                            url = org.url
                        }
                    }
                )

            }

            parameters.issueManagement.orNull?.let { issue ->
                issueManagement(
                    action = {
                        issueManagement {
                            system = issue.system
                            url = issue.url
                        }
                    }
                )
            }

            parameters.scm.orNull?.let { scm ->
                scm(
                    action = {
                        scm {
                            connection = scm.connection
                            developerConnection = scm.developerConnection
                            url = scm.url
                        }
                    }
                )
            }
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

            parameters.licenses.set(ext.licenses)

            parameters.developers.set(ext.developers)

            parameters.mailingLists.set(ext.mailingLists)

            parameters.organization.set(ext.organization ?: Organization())

            parameters.issueManagement.set(ext.issueManagement ?: IssueManagement())

            parameters.scm.set(ext.scm ?: Scm())
        }
    }
}
