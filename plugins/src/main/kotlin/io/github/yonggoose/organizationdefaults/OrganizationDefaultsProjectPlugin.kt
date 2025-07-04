package io.github.yonggoose.organizationdefaults

import io.github.yonggoose.organizationdefaults.spec.DevelopersContainer
import io.github.yonggoose.organizationdefaults.spec.MailingListsContainer
import org.gradle.api.Plugin
import org.gradle.api.Project

open class PomDefaultsExtension {
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
                license = organizationDefaultExtension.license,

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
                license = projectPomExt.license,

                organization = projectPomExt.organization,

                developers = projectPomExt.developers,

                issueManagement = projectPomExt.issueManagement,

                mailingLists = projectPomExt.mailingLists,

                scm = projectPomExt.scm
            )

            val merged = orgDefaults.merge(projectPom)
            project.extensions.extraProperties.set("mergedDefaults", merged)
        }
    }
}
