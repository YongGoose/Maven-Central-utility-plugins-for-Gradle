package io.github.yonggoose.organizationdefaults.spec

import io.github.yonggoose.organizationdefaults.MailingList

class MailingListSpec {
    var name: String? = null
    var subscribe: String? = null
    var unsubscribe: String? = null
    var post: String? = null
    var archive: String? = null

    fun build() = MailingList(name, subscribe, unsubscribe, post, archive)
}

class MailingListsContainer {
    private val mailingLists = mutableListOf<MailingList>()

    fun mailingList(action: MailingListSpec.() -> Unit) {
        val spec = MailingListSpec().apply(action)
        mailingLists.add(spec.build())
    }

    internal fun getMailingLists(): List<MailingList> = mailingLists
}
