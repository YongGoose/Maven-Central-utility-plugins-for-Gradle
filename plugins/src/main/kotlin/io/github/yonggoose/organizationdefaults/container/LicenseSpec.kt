package io.github.yonggoose.organizationdefaults.container

import io.github.yonggoose.organizationdefaults.License

class LicenseSpec {
    var licenseType: String? = null

    fun build() = License(licenseType)
}

class LicenseContainer {
    private val licenses = mutableListOf<License>()

    fun license(action: LicenseSpec.() -> Unit) {
        val spec = LicenseSpec().apply(action)
        licenses.add(spec.build())
    }

    internal fun getLicenses(): List<License> = licenses
}