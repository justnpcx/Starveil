package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
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

    override suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>) {
        rows.values.removeAll { it.groupId == groupId }
        profiles.forEach { upsert(it.copy(groupId = groupId)) }
    }
}

private class ImpGroups : GroupRepository {
    val rows = LinkedHashMap<Long, ProxyGroup>()
    override fun observeAll(): Flow<List<ProxyGroup>> = flow { emit(rows.values.toList()) }
    override suspend fun getById(id: Long): ProxyGroup? = rows[id]
    override suspend fun upsert(group: ProxyGroup): Long {
        val id = if (group.id == 0L) (rows.keys.maxOrNull() ?: 0L) + 1 else group.id
        rows[id] = group.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
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

class LinkImporterTest {

    private val resolver = LinkResolver { link ->
        if (link.startsWith("vless://")) {
            Profile(name = "node-from-link", type = ProfileType.VLESS, server = "h.example", port = 443)
        } else {
            null
        }
    }

    private fun importer(
        profiles: ImpProfiles = ImpProfiles(),
        groups: ImpGroups = ImpGroups(),
        fetcher: SubscriptionFetcher = ImpFetcher(error = IOException("HTTP 500")),
    ): Triple<LinkImporter, ImpProfiles, ImpGroups> {
        val registry = SubscriptionFormatRegistry.default { link -> resolver.resolve(link) }
        val updater = SubscriptionUpdater(
            registry = registry,
            profiles = profiles,
            health = ImpHealth(),
            fetcher = fetcher,
            clock = { 1L },
        )
        return Triple(LinkImporter(resolver, profiles, groups, updater), profiles, groups)
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
            "vless://a@h.example:443#n1\nvless://b@h.example:443#n2".toByteArray(),
        )
        val (import_, profiles, groups) = importer(fetcher = ImpFetcher(body = body))

        val outcome = import_.import("https://sub.example.com/x?token=t")

        assertEquals(ImportOutcome.AddedSubscription(2), outcome)
        assertEquals(2, profiles.rows.size)
        assertEquals(1, groups.rows.size)
        assertEquals("https://sub.example.com/x?token=t", groups.rows.values.single().subscriptionUrl)
    }

    @Test
    fun `a failed refresh reports the reason and stores nothing`() = runBlocking {
        val (import_, profiles, _) = importer(fetcher = ImpFetcher(error = IOException("HTTP 403")))

        val outcome = import_.import("https://sub.example.com/x")

        assertEquals(ImportOutcome.Failed("HTTP 403"), outcome)
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
