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
 * These properties are the `issueManagement { }` DSL surface — a build script's
 * `issueManagement { system = "..." }` assigns to them directly, so they must stay publicly
 * settable.
 */
class IssueManagementContainer {
    var system: String? = null
    var url: String? = null

    /**
     * Applies an [IssueManagementSpec] on top of the current values, leaving fields the spec
     * does not set untouched.
     */
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
