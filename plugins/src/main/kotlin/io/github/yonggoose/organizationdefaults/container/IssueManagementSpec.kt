package io.github.yonggoose.organizationdefaults.container

import io.github.yonggoose.organizationdefaults.IssueManagement

class IssueManagementSpec {
    var system: String? = null
    var url: String? = null
}

class IssueManagementContainer {
    var system: String? = null
    var url: String? = null

    internal fun getIssueManagement(): IssueManagement? {
        return IssueManagement(system, url)
    }
}