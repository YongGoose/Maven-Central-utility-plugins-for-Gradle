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
 */
class OrganizationContainer {
    var name: String? = null
    var url: String? = null

    fun organization(action: OrganizationSpec.() -> Unit) {
        val spec = OrganizationSpec().apply(action)
        this.name = spec.name
        this.url = spec.url
    }

    internal fun getOrganization(): Organization? {
        return Organization(name, url)
    }
}