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

    internal fun getLicenses(): List<License> = licenses
}