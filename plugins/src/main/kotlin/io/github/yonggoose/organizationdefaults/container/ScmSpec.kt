package io.github.yonggoose.organizationdefaults.container

import io.github.yonggoose.organizationdefaults.Scm

/**
 * Specifies scm information.
 */
class ScmSpec {
    var connection: String? = null
    var developerConnection: String? = null
    var url: String? = null
}

/**
 * Container class for managing scm.
 */
class ScmContainer {
    var connection: String? = null
    var developerConnection: String? = null
    var url: String? = null

    fun scm(action: ScmSpec.() -> Unit) {
        val spec = ScmSpec().apply(action)
        this.connection = spec.connection
        this.developerConnection = spec.developerConnection
        this.url = spec.url
    }

    internal fun getScm(): Scm? {
        return Scm(connection, developerConnection, url)
    }
}