package io.github.yonggoose.organizationdefaults

import java.io.Serializable

/**
 * Data class representing default organization information.
 *
 * @property groupId The group ID of the project.
 * @property artifactId The artifact ID of the project.
 * @property version The version of the project.
 * @property name The name of the project.
 * @property description The description of the project.
 * @property url The project URL.
 * @property inceptionYear The inception year of the project.
 * @property licenses List of licenses.
 * @property organization Organization information.
 * @property developers List of developers.
 * @property issueManagement Issue management information.
 * @property mailingLists List of mailing lists.
 * @property scm Source code management information.
 */
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
    /**
     * Merges the current values with the given [override] values.
     * Only non-null and non-empty values from [override] will overwrite the current values.
     *
     * @param override The values to override.
     * @return A new OrganizationDefaults instance with merged values.
     */
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

/**
 * Data class representing license information.
 *
 * @property licenseType The type of license.
 */
data class License(
    var licenseType: String? = null
) : Serializable

/**
 * Data class representing organization information.
 *
 * @property name The name of the organization.
 * @property url The URL of the organization.
 */
data class Organization(
    val name: String? = null,
    val url: String? = null
) : Serializable

/**
 * Data class representing developer information.
 *
 * @property id The developer's ID.
 * @property name The developer's name.
 * @property email The developer's email address.
 * @property url The developer's URL.
 * @property organization The developer's organization.
 * @property organizationUrl The URL of the developer's organization.
 * @property timezone The developer's timezone.
 */
data class Developer(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val url: String? = null,
    val organization: String? = null,
    val organizationUrl: String? = null,
    val timezone: String? = null
) : Serializable

/**
 * Data class representing issue management information.
 *
 * @property system The issue management system.
 * @property url The URL of the issue management system.
 */
data class IssueManagement(
    val system: String? = null,
    val url: String? = null
) : Serializable

/**
 * Data class representing mailing list information.
 *
 * @property name The name of the mailing list.
 * @property subscribe The subscription address.
 * @property unsubscribe The unsubscription address.
 * @property post The posting address.
 * @property archive The archive address.
 */
data class MailingList(
    val name: String? = null,
    val subscribe: String? = null,
    val unsubscribe: String? = null,
    val post: String? = null,
    val archive: String? = null,
) : Serializable

/**
 * Data class representing source code management information.
 *
 * @property connection The SCM connection information.
 * @property developerConnection The developer SCM connection information.
 * @property url The SCM URL.
 */
data class Scm(
    val connection: String? = null,
    val developerConnection: String? = null,
    val url: String? = null
) : Serializable
