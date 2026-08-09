package io.github.yonggoose.organizationdefaults.container

import io.github.yonggoose.organizationdefaults.Organization

/**
 * Specifies organization information.
 */
class OrganizationSpec {
    var name: String? = null
    var url: String? = null
}

/**
 * Container class for managing organization.
 *
 * Calling [organization] more than once merges into what was already configured: only the fields
 * the block actually sets are overwritten, so a second block cannot silently clear the first one.
 */
class OrganizationContainer {
    var name: String? = null
        private set
    var url: String? = null
        private set

    fun organization(action: OrganizationSpec.() -> Unit) {
        val spec = OrganizationSpec().apply(action)
        spec.name?.let { name = it }
        spec.url?.let { url = it }
    }

    /**
     * Returns `null` when nothing was configured, so that merging can distinguish
     * "not set" from "set to an empty organization block".
     */
    internal fun getOrganization(): Organization? {
        if (name == null && url == null) {
            return null
        }
        return Organization(name, url)
    }
}
