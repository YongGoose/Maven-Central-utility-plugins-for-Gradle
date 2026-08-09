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
 *
 * Calling [issueManagement] more than once merges into what was already configured: only the
 * fields the block actually sets are overwritten, so a second block cannot silently clear
 * the first one.
 */
class IssueManagementContainer {
    var system: String? = null
        private set
    var url: String? = null
        private set

    fun issueManagement(action: IssueManagementSpec.() -> Unit) {
        val spec = IssueManagementSpec().apply(action)
        spec.system?.let { system = it }
        spec.url?.let { url = it }
    }

    /**
     * Returns `null` when nothing was configured, so that merging can distinguish
     * "not set" from "set to an empty issueManagement block".
     */
    internal fun getIssueManagement(): IssueManagement? {
        if (system == null && url == null) {
            return null
        }
        return IssueManagement(system, url)
    }
}
