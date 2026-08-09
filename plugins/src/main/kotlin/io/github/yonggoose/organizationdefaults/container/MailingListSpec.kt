package io.github.yonggoose.organizationdefaults.container

import io.github.yonggoose.organizationdefaults.MailingList

/**
 * Specifies mailingList information.
 */
class MailingListSpec {
    var name: String? = null
    var subscribe: String? = null
    var unsubscribe: String? = null
    var post: String? = null
    var archive: String? = null

    fun build() = MailingList(name, subscribe, unsubscribe, post, archive)
}

/**
 * Container class for managing multiple mailingLists.
 */
class MailingListsContainer {
    private val mailingLists = mutableListOf<MailingList>()

    fun mailingList(action: MailingListSpec.() -> Unit) {
        val spec = MailingListSpec().apply(action)
        mailingLists.add(spec.build())
    }

    // Copied, not exposed: OrganizationDefaults is meant to be a snapshot, and merge() propagates
    // the same list instance to every module that inherits it.
    internal fun getMailingLists(): List<MailingList> = mailingLists.toList()
}
