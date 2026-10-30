package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProxyGroup
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Resolves a share link (`vless://…`) to a [Profile]. Injected by :app from the
 * ProtocolRegistry, so this module never depends on :core:kernel and a feature
 * module never has to either.
 */
fun interface LinkResolver {
    fun resolve(link: String): Profile?
}

/**
 * The inverse of [LinkResolver]: a [Profile] as a share link, or null when the
 * profile is not something any protocol can express.
 *
 * Injected by :app from the ProtocolRegistry, for the same reason as
 * [LinkResolver] — a feature module must not depend on :core:kernel.
 */
fun interface LinkExporter {
    fun export(profile: Profile): String?
}

/**
 * What a piece of text would import, without importing it.
 *
 * The add panel shows what is on the clipboard before the user commits to it,
 * and the tile that leads to the clipboard is greyed out when this is empty —
 * so the parse has to be available on its own, not only as a side effect of an
 * import.
 */
data class LinkPreview(
    /** Every node link the text resolved to, in order. */
    val nodes: List<Profile>,
    /** The first http(s) URL, which is a subscription rather than a node. */
    val subscriptionUrl: String?,
) {
    val isEmpty: Boolean get() = nodes.isEmpty() && subscriptionUrl == null
}

/** The result of importing several lines at once. */
data class BatchOutcome(
    val added: Int,
    /** The rows this batch created, in order. */
    val addedIds: List<Long> = emptyList(),
    /** Rows that were already stored, by id. */
    val duplicates: List<Long> = emptyList(),
    val failures: List<ImportFailure> = emptyList(),
)

sealed interface ImportOutcome {
    /** [id] is the stored row, so the caller can point at it. */
    data class AddedNode(val name: String, val id: Long = 0L) : ImportOutcome

    /** The link resolves to a node that is already stored. */
    data class Duplicate(val name: String, val id: Long) : ImportOutcome

    /** [dropped] is how many entries the subscription carried but could not be used. */
    data class AddedSubscription(val nodeCount: Int, val dropped: Int = 0) : ImportOutcome
    data class Failed(val failure: ImportFailure) : ImportOutcome
}

/** Why an import did nothing, as data so the screen owns the wording. */
sealed interface ImportFailure {
    data object NothingToImport : ImportFailure

    data object LinkUnparseable : ImportFailure

    data class Subscription(val reason: SubscriptionFailure) : ImportFailure
}

/**
 * The one entry point for "get nodes into the app": paste a share link, or
 * paste a subscription URL.
 *
 * A share link is resolved and stored directly. Anything http(s) is treated as
 * a subscription: a group is created (or reused) and one refresh is run, which
 * preserves the usual failure rule — a failed refresh never wipes the nodes
 * already stored.
 */
@Singleton
class LinkImporter @Inject constructor(
    private val resolver: LinkResolver,
    private val profiles: ProfileRepository,
    private val groups: GroupRepository,
    private val updater: SubscriptionUpdater,
    private val refreshScheduler: SubscriptionRefreshScheduler,
) {

    /**
     * Parses [text] without touching the database or the network.
     *
     * Whitespace and newlines both separate entries: a share sheet copies one
     * link, but a subscription's "export" page and a chat message both hand
     * over several at once, one per line.
     */
    fun preview(text: String): LinkPreview {
        val nodes = mutableListOf<Profile>()
        var subscription: String? = null
        for (token in splitLinks(text)) {
            if (isSubscriptionUrl(token)) {
                if (subscription == null) subscription = token
            } else {
                resolver.resolve(token)?.let { nodes += it }
            }
        }
        return LinkPreview(nodes = nodes, subscriptionUrl = subscription)
    }

    /**
     * Imports every entry in [text], reporting what failed rather than stopping
     * at the first bad line — a pasted block with one broken link should still
     * bring in the rest.
     */
    suspend fun importMany(text: String): BatchOutcome {
        var added = 0
        val addedIds = mutableListOf<Long>()
        val duplicates = mutableListOf<Long>()
        val failures = mutableListOf<ImportFailure>()
        for (token in splitLinks(text)) {
            when (val outcome = import(token)) {
                is ImportOutcome.AddedNode -> {
                    added++
                    addedIds += outcome.id
                }
                is ImportOutcome.Duplicate -> duplicates += outcome.id
                is ImportOutcome.AddedSubscription -> added += outcome.nodeCount
                is ImportOutcome.Failed -> failures += outcome.failure
            }
        }
        return BatchOutcome(
            added = added,
            addedIds = addedIds,
            duplicates = duplicates,
            failures = failures,
        )
    }

    suspend fun import(text: String, subscriptionName: String? = null): ImportOutcome {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ImportOutcome.Failed(ImportFailure.NothingToImport)

        return if (isSubscriptionUrl(trimmed)) {
            importSubscription(trimmed, subscriptionName)
        } else {
            importShareLink(trimmed)
        }
    }

    private suspend fun importShareLink(link: String): ImportOutcome {
        val profile = resolver.resolve(link)
            ?: return ImportOutcome.Failed(ImportFailure.LinkUnparseable)
        // A link pasted twice is one node, not two rows: the identity is what a
        // user would call "the same node" — protocol, endpoint and name.
        val existing = profiles.observeAll().first().firstOrNull { it.sameNodeAs(profile) }
        if (existing != null) return ImportOutcome.Duplicate(existing.name, existing.id)
        val id = profiles.upsert(profile)
        return ImportOutcome.AddedNode(profile.name, id)
    }

    /** Two profiles are the same node when a user would say so. */
    private fun Profile.sameNodeAs(other: Profile): Boolean =
        type == other.type && server == other.server && port == other.port && name == other.name

    private suspend fun importSubscription(url: String, name: String?): ImportOutcome {
        // One subscription URL is one group that refreshes. Inserting a fresh
        // group on every import split the same subscription's nodes across
        // duplicates — which showed up as three identical chips in the node
        // list after three imports.
        val existing = groups.findBySubscriptionUrl(url)
        val groupId = existing?.id
            ?: groups.upsert(
                ProxyGroup(
                    // A named subscription keeps its name; an unnamed one is
                    // called after its host, so two subscriptions are not both
                    // "Subscription" in the chip row.
                    name = name?.trim()?.takeIf { it.isNotEmpty() } ?: defaultName(url),
                    subscriptionUrl = url,
                ),
            )
        // Schedule before the first fetch: a group whose initial fetch fails
        // still has a URL, and must retry on the periodic cadence rather than
        // stay empty forever.
        refreshScheduler.schedule(groupId)
        return when (val outcome = updater.update(groupId, url)) {
            is SubscriptionUpdateOutcome.Success ->
                ImportOutcome.AddedSubscription(outcome.profileCount, outcome.dropped)
            is SubscriptionUpdateOutcome.Failure ->
                ImportOutcome.Failed(ImportFailure.Subscription(outcome.reason))
        }
    }

    /** The host of [url], or the generic label when it cannot be read. */
    private fun defaultName(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: SUBSCRIPTION_GROUP_NAME

    private fun isSubscriptionUrl(token: String): Boolean =
        token.startsWith("http://", ignoreCase = true) ||
            token.startsWith("https://", ignoreCase = true)

    private fun splitLinks(text: String): List<String> =
        text.split(WHITESPACE).map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        const val SUBSCRIPTION_GROUP_NAME = "Subscription"

        /** Newlines, tabs and spaces all separate entries in a pasted block. */
        val WHITESPACE = Regex("\\s+")
    }
}
