package io.github.yonggoose.organizationdefaults

import java.io.Serializable

data class OrganizationDefaults(
    val groupId: String? = null,
    val artifactId: String? = null,
    val version: String? = null,

    val name: String? = null,
    val description: String? = null,
    val url: String? = null,
    val inceptionYear: String? = null,

    val licenses: List<License> = emptyList(),

    val organization: Organization? = null,

    val developers: List<Developer> = emptyList(),

    val issueManagement: IssueManagement? = null,

    val mailingLists: List<MailingList> = emptyList(),

    val scm: Scm? = null
) : Serializable {
    fun merge(override: OrganizationDefaults?): OrganizationDefaults {
        if (override == null) return this
        return OrganizationDefaults(
            groupId = override.groupId ?: this.groupId,
            artifactId = override.artifactId ?: this.artifactId,
            version = override.version ?: this.version,
            name = override.name ?: this.name,
            description = override.description ?: this.description,
            url = override.url ?: this.url,
            inceptionYear = override.inceptionYear ?: this.inceptionYear,
            licenses = override.licenses.ifEmpty { this.licenses },
            organization = if (override.organization?.name == null && override.organization?.url == null) {
                this.organization
            } else {
                override.organization
            },
            developers = override.developers.ifEmpty { this.developers },
            issueManagement = if (override.issueManagement?.system == null && override.issueManagement?.url == null) {
                this.issueManagement
            } else {
                override.issueManagement
            },
            mailingLists = override.mailingLists.ifEmpty { this.mailingLists },
            scm = if (override.scm?.connection == null && override.scm?.developerConnection == null && override.scm?.url == null) {
                this.scm
            } else {
                override.scm
            }
        )
    }
}

data class License(
    var licenseType: String? = null
) : Serializable

data class Organization(
    val name: String? = null,
    val url: String? = null
) : Serializable

data class Developer(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val url: String? = null,
    val organization: String? = null,
    val organizationUrl: String? = null,
    val timezone: String? = null
) : Serializable

data class IssueManagement(
    val system: String? = null,
    val url: String? = null
) : Serializable

data class MailingList(
    val name: String? = null,
    val subscribe: String? = null,
    val unsubscribe: String? = null,
    val post: String? = null,
    val archive: String? = null,
) : Serializable

data class Scm(
    val connection: String? = null,
    val developerConnection: String? = null,
    val url: String? = null
) : Serializable
