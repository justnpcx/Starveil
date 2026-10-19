package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.NodeRegion
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.subscription.registry.SubscriptionFormatRegistry
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class ImpProfiles : ProfileRepository {
    val rows = LinkedHashMap<Long, Profile>()
    override fun observeAll(): Flow<List<Profile>> = flow { emit(rows.values.toList()) }
    override fun observeByGroup(groupId: Long): Flow<List<Profile>> = flow { emit(rows.values.filter { it.groupId == groupId }) }
    override suspend fun getById(id: Long): Profile? = rows[id]
    override suspend fun upsert(profile: Profile): Long {
        val id = if (profile.id == 0L) (rows.keys.maxOrNull() ?: 0L) + 1 else profile.id
        rows[id] = profile.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }

    override suspend fun deleteByGroup(groupId: Long) {
        rows.values.removeAll { it.groupId == groupId }
    }

    override suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>) {
        rows.values.removeAll { it.groupId == groupId }
        profiles.forEach { upsert(it.copy(groupId = groupId)) }
    }
}

private class ImpGroups : GroupRepository {
    val rows = LinkedHashMap<Long, ProxyGroup>()
    override fun observeAll(): Flow<List<ProxyGroup>> = flow { emit(rows.values.toList()) }
    override suspend fun getById(id: Long): ProxyGroup? = rows[id]
    override suspend fun findBySubscriptionUrl(url: String): ProxyGroup? =
        rows.values.firstOrNull { it.subscriptionUrl == url }

    override suspend fun findByRegion(region: NodeRegion): ProxyGroup? =
        rows.values.firstOrNull { it.region == region }
    override suspend fun upsert(group: ProxyGroup): Long {
        val id = if (group.id == 0L) (rows.keys.maxOrNull() ?: 0L) + 1 else group.id
        rows[id] = group.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }

    override suspend fun deleteWithProfiles(id: Long, deleteProfiles: Boolean) {
        rows.remove(id)
    }
}

private class ImpFetcher(
    private val body: String? = null,
    private val error: Exception? = null,
) : SubscriptionFetcher {
    override suspend fun fetch(url: String): String {
        error?.let { throw it }
        return body!!
    }
}

private class ImpHealth : SubscriptionHealthSink {
    override suspend fun recordSuccess(atEpochMs: Long) {}
    override suspend fun recordFailure(atEpochMs: Long, reason: String) {}
}

private class ImpRefreshScheduler : SubscriptionRefreshScheduler {
    val scheduled = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(groupId: Long) { scheduled += groupId }
    override fun cancel(groupId: Long) { cancelled += groupId }
}

class LinkImporterTest {

    private val resolver = LinkResolver { link ->
        if (link.startsWith("vless://")) {
            Profile(
                name = "node-from-link",
                type = ProfileType.VLESS,
                // The host comes from the link, as the real resolver's does:
                // a constant here makes every fixture node a duplicate.
                server = link.substringAfter('@').substringBefore(':'),
                port = 443,
            )
        } else {
            null
        }
    }

    private fun importer(
        profiles: ImpProfiles = ImpProfiles(),
        groups: ImpGroups = ImpGroups(),
        fetcher: SubscriptionFetcher = ImpFetcher(error = IOException("HTTP 500")),
        scheduler: SubscriptionRefreshScheduler = ImpRefreshScheduler(),
    ): Triple<LinkImporter, ImpProfiles, ImpGroups> {
        val registry = SubscriptionFormatRegistry.default { link -> resolver.resolve(link) }
        val updater = SubscriptionUpdater(
            registry = registry,
            profiles = profiles,
            health = ImpHealth(),
            fetcher = fetcher,
            clock = { 1L },
        )
        return Triple(LinkImporter(resolver, profiles, groups, updater, scheduler), profiles, groups)
    }

    @Test
    fun `a share link is resolved and stored`() = runBlocking {
        val (import_, profiles, _) = importer()

        val outcome = import_.import("vless://uuid@h.example:443#n")

        assertEquals(ImportOutcome.AddedNode("node-from-link"), outcome)
        assertEquals(1, profiles.rows.size)
        assertEquals(ProfileType.VLESS, profiles.rows.values.single().type)
    }

    @Test
    fun `a subscription url creates a group and imports its nodes`() = runBlocking {
        val body = Base64.getEncoder().encodeToString(
            "vless://a@h1.example:443#n1\nvless://b@h2.example:443#n2".toByteArray(),
        )
        val (import_, profiles, groups) = importer(fetcher = ImpFetcher(body = body))

        val outcome = import_.import("https://sub.example.com/x?token=t")

        assertEquals(ImportOutcome.AddedSubscription(2), outcome)
        assertEquals(2, profiles.rows.size)
        assertEquals(1, groups.rows.size)
        assertEquals("https://sub.example.com/x?token=t", groups.rows.values.single().subscriptionUrl)
    }

    @Test
    fun `importing the same subscription twice reuses its group`() = runBlocking {
        val body = Base64.getEncoder().encodeToString(
            "vless://a@h1.example:443#n1\nvless://b@h2.example:443#n2".toByteArray(),
        )
        val (import_, profiles, groups) = importer(fetcher = ImpFetcher(body = body))

        import_.import("https://sub.example.com/x")
        import_.import("https://sub.example.com/x")

        // One URL is one group that refreshes. A fresh group per import split
        // the subscription's nodes across duplicates — three identical chips in
        // the node list after three imports.
        assertEquals(1, groups.rows.size)
        assertEquals(2, profiles.rows.size)
    }

    @Test
    fun `a different subscription url gets its own group`() = runBlocking {
        val body = Base64.getEncoder().encodeToString("vless://a@h.example:443#n1".toByteArray())
        val (import_, _, groups) = importer(fetcher = ImpFetcher(body = body))

        import_.import("https://sub.example.com/one")
        import_.import("https://sub.example.com/two")

        assertEquals(2, groups.rows.size)
    }

    @Test
    fun `importing a subscription schedules its periodic refresh`() = runBlocking {
        val body = Base64.getEncoder().encodeToString("vless://a@h.example:443#n1".toByteArray())
        val scheduler = ImpRefreshScheduler()
        val (import_, _, groups) = importer(fetcher = ImpFetcher(body = body), scheduler = scheduler)

        import_.import("https://sub.example.com/x")

        // Without this the subscription is fetched exactly once, at import,
        // and never refreshes again.
        assertEquals(listOf(groups.rows.keys.single()), scheduler.scheduled)
    }

    @Test
    fun `a failed first fetch still schedules the retry`() = runBlocking {
        val scheduler = ImpRefreshScheduler()
        val (import_, _, groups) = importer(
            fetcher = ImpFetcher(error = IOException("HTTP 503")),
            scheduler = scheduler,
        )

        import_.import("https://sub.example.com/x")

        // The group exists with a URL but no nodes; the periodic work is what
        // gives it another chance.
        assertEquals(listOf(groups.rows.keys.single()), scheduler.scheduled)
    }

    @Test
    fun `a failed refresh reports the reason and stores nothing`() = runBlocking {
        val (import_, profiles, _) = importer(fetcher = ImpFetcher(error = HttpStatusException(403)))

        val outcome = import_.import("https://sub.example.com/x")

        assertEquals(
            ImportOutcome.Failed(ImportFailure.Subscription(SubscriptionFailure.Http(403))),
            outcome,
        )
        assertTrue(profiles.rows.isEmpty())
    }

    @Test
    fun `an unparseable share link fails cleanly`() = runBlocking {
        val (import_, profiles, _) = importer()

        val outcome = import_.import("vmess://not-supported-yet")

        assertTrue(outcome is ImportOutcome.Failed)
        assertTrue(profiles.rows.isEmpty())
    }

    @Test
    fun `blank input is rejected`() = runBlocking {
        val (import_, _, _) = importer()
        assertTrue(import_.import("   ") is ImportOutcome.Failed)
    }
}
