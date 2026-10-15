package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.subscription.registry.SubscriptionFormatRegistry
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeFetcher(
    private val body: String? = null,
    private val error: Exception? = null,
) : SubscriptionFetcher {
    var lastUrl: String? = null
    override suspend fun fetch(url: String): String {
        lastUrl = url
        error?.let { throw it }
        return body!!
    }
}

private class FakeProfiles(private val failOnReplace: Boolean = false) : ProfileRepository {
    val stored = mutableListOf<Profile>()
    var replaceCalls = 0
        private set

    override fun observeAll(): Flow<List<Profile>> = flow { emit(stored.toList()) }
    override fun observeByGroup(groupId: Long): Flow<List<Profile>> = flow { emit(stored.filter { it.groupId == groupId }) }
    override suspend fun getById(id: Long): Profile? = stored.firstOrNull { it.id == id }
    override suspend fun upsert(profile: Profile): Long {
        stored += profile
        return stored.size.toLong()
    }

    override suspend fun deleteById(id: Long) {
        stored.removeAll { it.id == id }
    }

    override suspend fun deleteByGroup(groupId: Long) {
        stored.removeAll { it.groupId == groupId }
    }

    override suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>) {
        replaceCalls++
        if (failOnReplace) throw IllegalStateException("db down")
        stored.clear()
        stored += profiles
    }
}

private class FakeHealth : SubscriptionHealthSink {
    var lastSuccessAt: Long? = null
    var lastFailureAt: Long? = null
    var lastReason: String? = null

    override suspend fun recordSuccess(atEpochMs: Long) {
        lastSuccessAt = atEpochMs
    }

    override suspend fun recordFailure(atEpochMs: Long, reason: String) {
        lastFailureAt = atEpochMs
        lastReason = reason
    }
}

class SubscriptionUpdaterTest {

    private val secretUrl = "https://sub.example.com/abc?token=SECRET-TOKEN"

    private fun updater(
        body: String? = null,
        error: Exception? = null,
        profiles: FakeProfiles = FakeProfiles(),
        health: FakeHealth = FakeHealth(),
        now: Long = 1_700_000_000_000L,
    ): Triple<SubscriptionUpdater, FakeProfiles, FakeHealth> {
        val registry = SubscriptionFormatRegistry.default { link ->
            if (link.startsWith("vless://")) {
                Profile(name = link.substringAfter('#', "n"), type = ProfileType.VLESS, server = "h", port = 443)
            } else {
                null
            }
        }
        val updater = SubscriptionUpdater(
            registry = registry,
            profiles = profiles,
            health = health,
            fetcher = FakeFetcher(body, error),
            clock = { now },
        )
        return Triple(updater, profiles, health)
    }

    private fun base64Links(vararg links: String): String =
        Base64.getEncoder().encodeToString(links.joinToString("\n").toByteArray())

    @Test
    fun `success replaces the group profiles and records the time`() = runBlocking {
        val body = base64Links("vless://u1@h:443#node1", "vless://u2@h:443#node2")
        val (updater, profiles, health) = updater(body = body)

        val outcome = updater.update(groupId = 7L, url = secretUrl)

        assertEquals(SubscriptionUpdateOutcome.Success(2), outcome)
        assertEquals(2, profiles.stored.size)
        assertEquals(1_700_000_000_000L, health.lastSuccessAt)
        assertEquals(null, health.lastReason)
    }

    @Test
    fun `fetch failure keeps last-good profiles and records a redacted reason`() = runBlocking {
        val (updater, profiles, health) = updater(error = IOException("HTTP 403"))

        val outcome = updater.update(groupId = 7L, url = secretUrl)

        assertEquals(SubscriptionUpdateOutcome.Failure("HTTP 403"), outcome)
        assertEquals(0, profiles.replaceCalls)
        assertTrue(profiles.stored.isEmpty())
        assertEquals("HTTP 403", health.lastReason)
        // The URL/token must never leak into the stored reason.
        assertFalse(health.lastReason!!.contains("SECRET-TOKEN"))
        assertFalse(health.lastReason!!.contains("sub.example.com"))
    }

    @Test
    fun `network error without an HTTP status is reported generically`() = runBlocking {
        val (updater, _, health) = updater(error = IOException("Connection refused"))
        updater.update(groupId = 1L, url = secretUrl)
        assertEquals("network error", health.lastReason)
    }

    @Test
    fun `a subscription that parses to nothing is a failure, not a wipe`() = runBlocking {
        val (updater, profiles, health) = updater(body = "not a subscription at all")

        val outcome = updater.update(groupId = 7L, url = secretUrl)

        assertTrue(outcome is SubscriptionUpdateOutcome.Failure)
        assertEquals(0, profiles.replaceCalls)
        assertEquals("subscription contained no usable nodes", health.lastReason)
    }

    @Test
    fun `a save failure is reported and does not record success`() = runBlocking {
        val body = base64Links("vless://u1@h:443#node1")
        val (updater, _, health) = updater(body = body, profiles = FakeProfiles(failOnReplace = true))

        val outcome = updater.update(groupId = 7L, url = secretUrl)

        assertEquals(SubscriptionUpdateOutcome.Failure("could not save nodes"), outcome)
        assertEquals(null, health.lastSuccessAt)
        assertEquals("could not save nodes", health.lastReason)
    }
}

class SubscriptionSchedulerConfigTest {

    @Test
    fun `refresh runs every 6 hours with a 10 minute flex window`() {
        assertEquals(6L, SubscriptionUpdateScheduler.INTERVAL_HOURS)
        assertEquals(10L, SubscriptionUpdateScheduler.FLEX_MINUTES)
    }

    @Test
    fun `each group gets its own unique work name`() {
        // A single shared name made the second subscription's schedule replace
        // the first's, so the first group silently stopped refreshing.
        val a = SubscriptionUpdateScheduler.workName(1L)
        val b = SubscriptionUpdateScheduler.workName(2L)

        assertNotEquals(a, b)
        assertTrue(a.startsWith(SubscriptionUpdateScheduler.WORK_NAME))
        assertEquals(SubscriptionUpdateScheduler.workName(7L), SubscriptionUpdateScheduler.workName(7L))
    }
}
