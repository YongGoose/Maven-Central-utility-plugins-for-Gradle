package io.github.yonggoose.organizationdefaults.container

import io.github.yonggoose.organizationdefaults.License

/**
 * Specifies license information.
 */
class LicenseSpec {
    var name: String? = null
    var url: String? = null
    var distribution: String? = null
    var comments: String? = null

    fun build() = License(name, url, distribution, comments)
}

/**
 * Container class for managing multiple licenses.
 */
class LicenseContainer {
    private val licenses = mutableListOf<License>()

    fun license(action: LicenseSpec.() -> Unit) {
        val spec = LicenseSpec().apply(action)
        licenses.add(spec.build())
    }

    // Copied, not exposed: OrganizationDefaults is meant to be a snapshot, and merge() propagates
    // the same list instance to every module that inherits it.
    internal fun getLicenses(): List<License> = licenses.toList()
}
