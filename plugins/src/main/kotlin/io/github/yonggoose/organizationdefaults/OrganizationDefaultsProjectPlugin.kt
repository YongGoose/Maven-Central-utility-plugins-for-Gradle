package io.github.yonggoose.organizationdefaults

import io.github.yonggoose.organizationdefaults.container.DevelopersContainer
import io.github.yonggoose.organizationdefaults.container.IssueManagementContainer
import io.github.yonggoose.organizationdefaults.container.LicenseContainer
import io.github.yonggoose.organizationdefaults.container.MailingListsContainer
import io.github.yonggoose.organizationdefaults.container.OrganizationContainer
import io.github.yonggoose.organizationdefaults.container.ScmContainer
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Extension for specifying default POM metadata for Gradle projects.
 */
open class PomDefaultsExtension {
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
        get() = issueManagementContainer.getIssueManagement()
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
        issueManagementContainer.action()
    }

    fun organization(action: OrganizationContainer.() -> Unit) {
        organizationContainer.action()
    }

    fun scm(action: ScmContainer.() -> Unit) {
        scmContainer.action()
    }
}

/**
 * Gradle plugin for providing and merging organization-wide default POM metadata into projects.
 */
class OrganizationDefaultsProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val projectPomExt = project.extensions.create("projectPom", PomDefaultsExtension::class.java)

        if (project == project.rootProject) {
            project.extensions.create("rootProjectPom", PomDefaultsExtension::class.java)
        }

        project.afterEvaluate {
            val organizationDefaultExtension = project.rootProject.extensions.findByName("rootProjectPom") as? PomDefaultsExtension
                ?: PomDefaultsExtension()

            val orgDefaults = OrganizationDefaults(
                groupId = organizationDefaultExtension.groupId,
                artifactId = organizationDefaultExtension.artifactId,
                version = organizationDefaultExtension.version,

                name = organizationDefaultExtension.name,
                description = organizationDefaultExtension.description,
                url = organizationDefaultExtension.url,
                inceptionYear = organizationDefaultExtension.inceptionYear,

                licenses = organizationDefaultExtension.licenses,

                organization = organizationDefaultExtension.organization,

                developers = organizationDefaultExtension.developers,

                issueManagement = organizationDefaultExtension.issueManagement,

                mailingLists = organizationDefaultExtension.mailingLists,

                scm = organizationDefaultExtension.scm
            )

            val projectPom = OrganizationDefaults(
                groupId = projectPomExt.groupId,
                artifactId = projectPomExt.artifactId,
                version = projectPomExt.version,

                name = projectPomExt.name,
                description = projectPomExt.description,
                url = projectPomExt.url,
                inceptionYear = projectPomExt.inceptionYear,

                licenses = projectPomExt.licenses,

                developers = projectPomExt.developers,

                mailingLists = projectPomExt.mailingLists,

                organization = projectPomExt.organization,

                issueManagement = projectPomExt.issueManagement,

                scm = projectPomExt.scm
            )

            val merged = orgDefaults.merge(projectPom)
            project.extensions.extraProperties.set("mergedDefaults", merged)
        }
    }
}
