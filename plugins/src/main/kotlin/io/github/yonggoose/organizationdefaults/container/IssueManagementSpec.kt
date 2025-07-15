package io.github.yonggoose.organizationdefaults.container

import io.github.yonggoose.organizationdefaults.IssueManagement

/**
 * Specifies issueManagement information.
 */
class IssueManagementSpec {
    var system: String? = null
    var url: String? = null
}

/**
 * Container class for managing issueManagement.
 */
class IssueManagementContainer {
    var system: String? = null
    var url: String? = null

    fun issueManagement(action: IssueManagementSpec.() -> Unit) {
        val spec = IssueManagementSpec().apply(action)
        this.system = spec.system
        this.url = spec.url
    }

    internal fun getIssueManagement(): IssueManagement? {
        return IssueManagement(system, url)
    }
}