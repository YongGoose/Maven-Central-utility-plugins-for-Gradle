package io.github.yonggoose.organizationdefaults

import io.github.yonggoose.organizationdefaults.container.DevelopersContainer
import io.github.yonggoose.organizationdefaults.container.IssueManagementContainer
import io.github.yonggoose.organizationdefaults.container.LicenseContainer
import io.github.yonggoose.organizationdefaults.container.MailingListsContainer
import io.github.yonggoose.organizationdefaults.container.OrganizationContainer
import io.github.yonggoose.organizationdefaults.container.ScmContainer

/**
 * The POM metadata DSL shared by the settings-level and the project-level extensions.
 *
 * Both `rootProjectSetting` (settings plugin), `rootProjectPom` and `projectPom` (project plugin)
 * expose exactly the same set of POM elements, so the DSL lives here once instead of being
 * duplicated per extension.
 */
abstract class AbstractPomMetadataExtension {
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
    private val issueManagementContainer = IssueManagementContainer()
    private val organizationContainer = OrganizationContainer()
    private val scmContainer = ScmContainer()

    val licenses: List<License>
        get() = licenseContainer.getLicenses()

    val developers: List<Developer>
        get() = developersContainer.getDevelopers()

    val mailingLists: List<MailingList>
        get() = mailingListsContainer.getMailingLists()

    val issueManagement: IssueManagement?
        get() = issueManagementContainer.getIssueManagement()

    val organization: Organization?
        get() = organizationContainer.getOrganization()

    val scm: Scm?
        get() = scmContainer.getScm()

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
        issueManagementContainer.action()
    }

    fun organization(action: OrganizationContainer.() -> Unit) {
        organizationContainer.action()
    }

    fun scm(action: ScmContainer.() -> Unit) {
        scmContainer.action()
    }

    /**
     * Snapshots the currently configured metadata into an immutable [OrganizationDefaults].
     */
    fun toOrganizationDefaults(): OrganizationDefaults =
        OrganizationDefaults(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            name = name,
            description = description,
            url = url,
            inceptionYear = inceptionYear,
            licenses = licenses,
            organization = organization,
            developers = developers,
            issueManagement = issueManagement,
            mailingLists = mailingLists,
            scm = scm
        )
}
