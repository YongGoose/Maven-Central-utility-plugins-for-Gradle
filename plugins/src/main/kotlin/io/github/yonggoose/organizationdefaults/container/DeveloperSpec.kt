package io.github.yonggoose.organizationdefaults.container

import io.github.yonggoose.organizationdefaults.Developer

/**
 * Specifies developer information.
 */
class DeveloperSpec {
    var id: String? = null
    var name: String? = null
    var email: String? = null
    var url: String? = null
    var organization: String? = null
    var organizationUrl: String? = null
    var timezone: String? = null

    fun build() = Developer(id, name, email, url, organization, organizationUrl, timezone)
}

/**
 * Container class for managing multiple developers.
 */
class DevelopersContainer {
    private val developers = mutableListOf<Developer>()

    fun developer(action: DeveloperSpec.() -> Unit) {
        val spec = DeveloperSpec().apply(action)
        developers.add(spec.build())
    }

    internal fun getDevelopers(): List<Developer> = developers
}
