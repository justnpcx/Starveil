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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeFetcher(
    private val body: String? = null,
    private val error: Exception? = null,
    private val userInfoHeader: String? = null,
) : SubscriptionFetcher {
    var lastUrl: String? = null
    override suspend fun fetch(url: String): String = fetchWithMeta(url).body
    override suspend fun fetchWithMeta(url: String): FetchedSubscription {
        lastUrl = url
        error?.let { throw it }
        return FetchedSubscription(body!!, userInfoHeader)
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

    override suspend fun setFavorite(id: Long, favorite: Boolean) {
        val index = stored.indexOfFirst { it.id == id }
        if (index >= 0) stored[index] = stored[index].copy(favorite = favorite)
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
        userInfo: String? = null,
        profiles: FakeProfiles = FakeProfiles(),
        health: FakeHealth = FakeHealth(),
        now: Long = 1_700_000_000_000L,
    ): Triple<SubscriptionUpdater, FakeProfiles, FakeHealth> {
        val registry = SubscriptionFormatRegistry.default { link ->
            if (link.startsWith("vless://")) {
                Profile(
                    name = link.substringAfter('#', "n"),
                    type = ProfileType.VLESS,
                    // The real resolver reads the host out of the link; a fake
                    // that returned a constant would make every fixture node a
                    // duplicate of every other.
                    server = link.substringAfter('@').substringBefore(':'),
                    port = 443,
                )
            } else {
                null
            }
        }
        val updater = SubscriptionUpdater(
            registry = registry,
            profiles = profiles,
            health = health,
            fetcher = FakeFetcher(body, error, userInfo),
            clock = { now },
        )
        return Triple(updater, profiles, health)
    }

    private fun base64Links(vararg links: String): String =
        Base64.getEncoder().encodeToString(links.joinToString("\n").toByteArray())


    @Test
    fun `the quota header rides along with a successful refresh`() = runBlocking {
        val body = base64Links("vless://u1@h1:443#node1")
        val (updater, _, _) = updater(
            body = body,
            userInfo = "upload=10; download=20; total=100; expire=1893456000",
        )

        val outcome = updater.update(groupId = 1L, url = "https://s.example/sub")

        val success = outcome as SubscriptionUpdateOutcome.Success
        assertEquals(30L, success.userInfo?.usedBytes)
        assertEquals(100L, success.userInfo?.totalBytes)
    }

    @Test
    fun `no header means no quota, not a zero quota`() = runBlocking {
        val body = base64Links("vless://u1@h1:443#node1")
        val (updater, _, _) = updater(body = body)

        val outcome = updater.update(groupId = 1L, url = "https://s.example/sub")

        val success = outcome as SubscriptionUpdateOutcome.Success
        assertNull(success.userInfo)
    }

    @Test
    fun `success replaces the group profiles and records the time`() = runBlocking {
        val body = base64Links("vless://u1@h1:443#node1", "vless://u2@h2:443#node2")
        val (updater, profiles, health) = updater(body = body)

        val outcome = updater.update(groupId = 7L, url = secretUrl)

        assertEquals(SubscriptionUpdateOutcome.Success(2), outcome)
        assertEquals(2, profiles.stored.size)
        assertEquals(1_700_000_000_000L, health.lastSuccessAt)
        assertEquals(null, health.lastReason)
    }

    @Test
    fun `fetch failure keeps last-good profiles and records a redacted reason`() = runBlocking {
        val (updater, profiles, health) = updater(error = HttpStatusException(403))

        val outcome = updater.update(groupId = 7L, url = secretUrl)

        assertEquals(SubscriptionUpdateOutcome.Failure(SubscriptionFailure.Http(403)), outcome)
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
    fun `a malformed document reports its format and line, never the body`() = runBlocking {
        // A real YAML error with a real mark, and a token in the body that must
        // not end up in the stored reason.
        val body = "proxies: [oops\n# SECRET-TOKEN\n"
        val (updater, profiles, health) = updater(body = body)

        val outcome = updater.update(groupId = 7L, url = secretUrl)

        val failure = (outcome as SubscriptionUpdateOutcome.Failure).reason
        assertTrue(failure is SubscriptionFailure.MalformedDocument)
        failure as SubscriptionFailure.MalformedDocument
        assertEquals(DocumentFormat.YAML, failure.format)
        assertNotNull("SnakeYAML reports a line for this failure", failure.line)
        assertEquals(0, profiles.replaceCalls)
        assertFalse(health.lastReason!!.contains("SECRET-TOKEN"))
        assertFalse(health.lastReason!!.contains("oops"))
    }

    @Test
    fun `a save failure is reported and does not record success`() = runBlocking {
        val body = base64Links("vless://u1@h:443#node1")
        val (updater, _, health) = updater(body = body, profiles = FakeProfiles(failOnReplace = true))

        val outcome = updater.update(groupId = 7L, url = secretUrl)

        assertEquals(SubscriptionUpdateOutcome.Failure(SubscriptionFailure.SaveFailed), outcome)
        assertEquals(null, health.lastSuccessAt)
        assertEquals("could not save nodes", health.lastReason)
    }

    @Test
    fun `refused entries are reported alongside the imported count`() = runBlocking {
        val body = base64Links("vless://u1@h1:443#node1", "ss://x@h2:8388#node2", "vless://u3@h3:443#node3")
        val (updater, profiles, health) = updater(body = body)

        val outcome = updater.update(groupId = 7L, url = secretUrl)

        // The fake resolver only understands vless, so the ss line is the one
        // the parser refused. It must not be reported as a failed refresh:
        // two nodes arrived, and the user still gets them.
        assertEquals(SubscriptionUpdateOutcome.Success(2, dropped = 1), outcome)
        assertEquals(2, profiles.stored.size)
        assertEquals(1_700_000_000_000L, health.lastSuccessAt)
    }

    @Test
    fun `a duplicate is deduplicated, not counted as a failure`() = runBlocking {
        val body = base64Links("vless://u1@h1:443#node1", "vless://u2@h1:443#node1-longer")
        val (updater, profiles, _) = updater(body = body)

        val outcome = updater.update(groupId = 7L, url = secretUrl)

        // Two links, one server: one node stored, nothing dropped.
        assertEquals(SubscriptionUpdateOutcome.Success(1, dropped = 0), outcome)
        assertEquals(1, profiles.stored.size)
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
