package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProxyGroup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a share link (`vless://…`) to a [Profile]. Injected by :app from the
 * ProtocolRegistry, so this module never depends on :core:kernel and a feature
 * module never has to either.
 */
fun interface LinkResolver {
    fun resolve(link: String): Profile?
}

sealed interface ImportOutcome {
    data class AddedNode(val name: String) : ImportOutcome
    data class AddedSubscription(val nodeCount: Int) : ImportOutcome
    data class Failed(val reason: String) : ImportOutcome
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

    suspend fun import(text: String): ImportOutcome {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ImportOutcome.Failed("Nothing to import")

        val isSubscriptionUrl = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)

        return if (isSubscriptionUrl) {
            importSubscription(trimmed)
        } else {
            importShareLink(trimmed)
        }
    }

    private suspend fun importShareLink(link: String): ImportOutcome {
        val profile = resolver.resolve(link)
            ?: return ImportOutcome.Failed("This share link could not be parsed")
        profiles.upsert(profile)
        return ImportOutcome.AddedNode(profile.name)
    }

    private suspend fun importSubscription(url: String): ImportOutcome {
        // One subscription URL is one group that refreshes. Inserting a fresh
        // group on every import split the same subscription's nodes across
        // duplicates — which showed up as three identical chips in the node
        // list after three imports.
        val existing = groups.findBySubscriptionUrl(url)
        val groupId = existing?.id
            ?: groups.upsert(ProxyGroup(name = SUBSCRIPTION_GROUP_NAME, subscriptionUrl = url))
        // Schedule before the first fetch: a group whose initial fetch fails
        // still has a URL, and must retry on the periodic cadence rather than
        // stay empty forever.
        refreshScheduler.schedule(groupId)
        return when (val outcome = updater.update(groupId, url)) {
            is SubscriptionUpdateOutcome.Success -> ImportOutcome.AddedSubscription(outcome.profileCount)
            is SubscriptionUpdateOutcome.Failure -> ImportOutcome.Failed(outcome.reason)
        }
    }

    private companion object {
        const val SUBSCRIPTION_GROUP_NAME = "Subscription"
    }
}
