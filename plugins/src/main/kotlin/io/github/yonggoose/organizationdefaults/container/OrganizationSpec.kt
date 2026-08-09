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
 * These properties are the `organization { }` DSL surface — a build script's
 * `organization { name = "..." }` assigns to them directly, so they must stay publicly settable.
 */
class OrganizationContainer {
    var name: String? = null
    var url: String? = null

    /**
     * Applies an [OrganizationSpec] on top of the current values, leaving fields the spec does
     * not set untouched.
     */
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
