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
 * Calling [scm] more than once merges into what was already configured: only the fields the
 * block actually sets are overwritten, so a second block cannot silently clear the first one.
 */
class ScmContainer {
    var connection: String? = null
        private set
    var developerConnection: String? = null
        private set
    var url: String? = null
        private set

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
