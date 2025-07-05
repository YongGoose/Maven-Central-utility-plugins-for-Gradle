package io.github.yonggoose.organizationdefaults

data class OrganizationDefaults(
    val groupId: String? = null,
    val artifactId: String? = null,
    val version: String? = null,

    val name: String? = null,
    val description: String? = null,
    val url: String? = null,
    val inceptionYear: String? = null,
    val license: String? = null,

    val organization: Organization? = null,

    val developers: List<Developer> = emptyList(),

    val issueManagement: IssueManagement? = null,

    val mailingLists: List<MailingList> = emptyList(),

    val scm: Scm? = null
) {
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
            license = override.license ?: this.license,
            organization = override.organization ?: this.organization,
            developers = override.developers.ifEmpty { this.developers },
            issueManagement = override.issueManagement ?: this.issueManagement,
            mailingLists = override.mailingLists.ifEmpty { this.mailingLists },
            scm = override.scm ?: this.scm
        )
    }
}

data class Organization(
    val name: String? = null,
    val url: String? = null
)

data class Developer(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val url: String? = null,
    val organization: String? = null,
    val organizationUrl: String? = null,
    val timezone: String? = null
)

data class IssueManagement(
    val system: String? = null,
    val url: String? = null
)

data class MailingList(
    val name: String? = null,
    val subscribe: String? = null,
    val unsubscribe: String? = null,
    val post: String? = null,
    val archive: String? = null,
)

data class Scm(
    val connection: String? = null,
    val developerConnection: String? = null,
    val url: String? = null
)
