package io.github.yonggoose.organizationdefaults.container

import io.github.yonggoose.organizationdefaults.Scm

class ScmSpec {
    var connection: String? = null
    var developerConnection: String? = null
    var url: String? = null
}

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