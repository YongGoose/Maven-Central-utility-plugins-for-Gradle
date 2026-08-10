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
 *
 * These properties are the `scm { }` DSL surface — a build script's
 * `scm { url = "..." }` assigns to them directly, so they must stay publicly settable.
 */
class ScmContainer {
    var connection: String? = null
    var developerConnection: String? = null
    var url: String? = null

    /**
     * Applies a [ScmSpec] on top of the current values, leaving fields the spec does not set
     * untouched.
     */
    fun scm(action: ScmSpec.() -> Unit) {
        val spec = ScmSpec().apply(action)
        spec.connection?.let { connection = it }
        spec.developerConnection?.let { developerConnection = it }
        spec.url?.let { url = it }
    }

    /**
     * Returns `null` when nothing was configured, so that merging can distinguish
     * "not set" from "set to an empty scm block".
     */
    internal fun getScm(): Scm? {
        if (connection == null && developerConnection == null && url == null) {
            return null
        }
        return Scm(connection, developerConnection, url)
    }
}
