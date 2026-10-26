package com.justnpcx.starveil.core.probe

import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.AppRoute
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Scene
import com.justnpcx.starveil.core.model.LATENCY_UNREACHABLE
import com.justnpcx.starveil.core.model.LatencySample
import com.justnpcx.starveil.core.model.LeakCheckRecord
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.NodeHealth
import com.justnpcx.starveil.core.model.ProfileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sampler's decisions: what it records, what it skips, and that it keeps
 * going. The TCP handshake itself is [NodeProbe]'s, and is not simulated here.
 */
class NodeHealthSamplerTest {

    private val node = Profile(
        id = 1L,
        name = "reality-node",
        type = ProfileType.VLESS,
        server = "r.example.com",
        port = 443,
    )

    private fun sampler(
        probe: NodeProbe = FakeProbe(ProbeResult.Reached(42)),
        profiles: ProfileRepository = FakeProfiles(mapOf(1L to node)),
        selectedId: Long = 1L,
        health: NodeHealthRepository = FakeHealth(),
    ) = NodeHealthSampler(
        probe = probe,
        profiles = profiles,
        preferences = FakePreferences(selectedId),
        health = health,
    )

    @Test
    fun `a reachable probe is recorded as its round-trip`() = runTest {
        val health = FakeHealth()
        val id = sampler(probe = FakeProbe(ProbeResult.Reached(87)), health = health)
            .sampleOnce(atMs = 5_000L)

        assertEquals(1L, id)
        assertEquals(listOf(Triple(1L, 87L, 5_000L)), health.recorded)
    }

    @Test
    fun `a timeout is recorded as loss, not skipped`() = runTest {
        // The loss dots are made of these; dropping them would make an
        // unreliable node look perfect.
        val health = FakeHealth()
        sampler(probe = FakeProbe(ProbeResult.TimedOut), health = health).sampleOnce(atMs = 1L)

        assertEquals(LATENCY_UNREACHABLE, health.recorded.single().second)
    }

    @Test
    fun `an unreachable node is recorded as loss too`() = runTest {
        val health = FakeHealth()
        sampler(probe = FakeProbe(ProbeResult.Failed), health = health).sampleOnce(atMs = 1L)

        assertEquals(LATENCY_UNREACHABLE, health.recorded.single().second)
    }

    @Test
    fun `nothing selected means nothing sampled`() = runTest {
        val health = FakeHealth()
        val probe = FakeProbe(ProbeResult.Reached(10))

        val id = sampler(probe = probe, selectedId = 0L, health = health).sampleOnce(atMs = 1L)

        assertNull(id)
        assertTrue(health.recorded.isEmpty())
        // A group target reads as "no node selected", and probing a group's
        // members is a batch job, not this.
        assertEquals(0, probe.calls)
    }

    @Test
    fun `a node that is gone is not sampled`() = runTest {
        val health = FakeHealth()
        val id = sampler(
            profiles = FakeProfiles(emptyMap()),
            selectedId = 1L,
            health = health,
        ).sampleOnce(atMs = 1L)

        assertNull(id)
        assertTrue(health.recorded.isEmpty())
    }

    @Test
    fun `a WireGuard endpoint is not probed at all`() = runTest {
        // It speaks UDP only, so a TCP handshake can only fail — recording that
        // would be a lie about a node that works.
        val wg = node.copy(type = ProfileType.WIREGUARD)
        val probe = FakeProbe(ProbeResult.Failed)
        val health = FakeHealth()

        val id = sampler(
            probe = probe,
            profiles = FakeProfiles(mapOf(1L to wg)),
            health = health,
        ).sampleOnce(atMs = 1L)

        assertNull(id)
        assertEquals(0, probe.calls)
        assertTrue(health.recorded.isEmpty())
    }

    @Test
    fun `the first curve is filled inside a minute, then the cadence settles`() = runTest {
        val health = FakeHealth()
        val sampler = sampler(health = health)

        val job = launch { sampler.run() }
        // Five points at the burst interval: the minimum the chart needs.
        advanceTimeBy(NodeHealthSampler.BURST_INTERVAL_MS * (NodeHealth.MIN_SAMPLES - 1) + 1)
        assertEquals(NodeHealth.MIN_SAMPLES, health.recorded.size)

        // The sixth waits the steady interval.
        advanceTimeBy(NodeHealthSampler.INTERVAL_MS)
        assertEquals(NodeHealth.MIN_SAMPLES + 1, health.recorded.size)
        job.cancel()
    }

    @Test
    fun `cancelling the loop stops the sampling`() = runTest {
        val health = FakeHealth()
        val sampler = sampler(health = health)

        val job = launch { sampler.run() }
        advanceTimeBy(1)
        job.cancel()
        val afterCancel = health.recorded.size

        advanceTimeBy(NodeHealthSampler.INTERVAL_MS * 5)
        assertEquals(afterCancel, health.recorded.size)
    }
}

private class FakeProbe(private val result: ProbeResult) : NodeProbe {
    var calls = 0
        private set

    override suspend fun probe(server: String, port: Int, timeoutMs: Int): ProbeResult {
        calls++
        return result
    }
}

private class FakeProfiles(private val byId: Map<Long, Profile>) : ProfileRepository {
    override fun observeAll(): Flow<List<Profile>> = samplerNotUsed()
    override fun observeByGroup(groupId: Long): Flow<List<Profile>> = samplerNotUsed()
    override suspend fun getById(id: Long): Profile? = byId[id]
    override suspend fun upsert(profile: Profile): Long = samplerNotUsed()
    override suspend fun deleteById(id: Long) = samplerNotUsed()
    override suspend fun setFavorite(id: Long, favorite: Boolean) = samplerNotUsed()
    override suspend fun deleteByGroup(groupId: Long) = samplerNotUsed()
    override suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>) = samplerNotUsed()
}

private class FakeHealth : NodeHealthRepository {
    val recorded = mutableListOf<Triple<Long, Long, Long>>()

    override fun observeAll(): Flow<Map<Long, List<LatencySample>>> = samplerNotUsed()
    override suspend fun record(
        profileId: Long,
        latencyMs: Long,
        atMs: Long,
        weightTenths: Int,
    ) {
        recorded += Triple(profileId, latencyMs, atMs)
    }

    override suspend fun forget(profileId: Long) = samplerNotUsed()
}

/** Only the selection matters to the sampler. */
private class FakePreferences(selectedId: Long) : PreferencesRepository {
    override val selectedProfileId: Flow<Long> = flowOf(selectedId)

    override val autoReconnect: Flow<Boolean> get() = samplerNotUsed()
    override val autoSwitchOnDrop: Flow<Boolean> get() = samplerNotUsed()
    override val bypassMainland: Flow<Boolean> get() = samplerNotUsed()
    override val routePreset: Flow<RoutePreset> = flowOf(RoutePreset.GLOBAL_PROXY)
    override val blockAds: Flow<Boolean> = flowOf(false)
    override val subscribedRulePacks: Flow<Set<String>> = flowOf(emptySet())
    override val localApiEnabled: Flow<Boolean> = flowOf(false)
    override val dnsMetricsEnabled: Flow<Boolean> = flowOf(true)
    override val onboardingDone: Flow<Boolean> = flowOf(true)
    override val killSwitchMode: Flow<KillSwitchMode> get() = samplerNotUsed()
    override val selectedGroupId: Flow<Long> get() = samplerNotUsed()
    override val connectionTarget: Flow<ConnectionTarget> get() = samplerNotUsed()
    override val lastLeakCheck: Flow<LeakCheckRecord> get() = samplerNotUsed()
    override val scenes: Flow<List<Scene>> = flowOf(emptyList())
    override suspend fun setScenes(scenes: List<Scene>) { samplerNotUsed() }
    override val manualPresetAt: Flow<Long> = flowOf(0L)
    override suspend fun recordManualPresetChange(atEpochMs: Long) { samplerNotUsed() }
    override val lastHealthProbeAt: Flow<Long> get() = flowOf(0L)
    override suspend fun recordHealthProbe(atEpochMs: Long) { samplerNotUsed() }
    override val subscriptionLastSuccessAt: Flow<Long> get() = samplerNotUsed()
    override val subscriptionLastFailureAt: Flow<Long> get() = samplerNotUsed()
    override val subscriptionLastError: Flow<String?> get() = samplerNotUsed()
    override val perAppRoutes: Flow<Map<String, AppRoute>> get() = samplerNotUsed()
    override suspend fun setBypassMainland(enabled: Boolean) = samplerNotUsed()
    override suspend fun setRoutePreset(preset: RoutePreset) { samplerNotUsed() }
    override suspend fun setBlockAds(enabled: Boolean) { samplerNotUsed() }
    override suspend fun setRulePackSubscribed(id: String, subscribed: Boolean) { samplerNotUsed() }
    override suspend fun setLocalApiEnabled(enabled: Boolean) { samplerNotUsed() }
    override suspend fun setDnsMetricsEnabled(enabled: Boolean) {}
    override suspend fun setOnboardingDone(done: Boolean) { samplerNotUsed() }
    override suspend fun setAutoReconnect(enabled: Boolean) = samplerNotUsed()
    override suspend fun setAutoSwitchOnDrop(enabled: Boolean) = samplerNotUsed()
    override suspend fun setKillSwitchMode(mode: KillSwitchMode) = samplerNotUsed()
    override suspend fun recordLeakCheck(record: LeakCheckRecord) = samplerNotUsed()
    override suspend fun setSelectedProfileId(id: Long) = samplerNotUsed()
    override suspend fun setSelectedGroupId(id: Long) = samplerNotUsed()
    override suspend fun recordSubscriptionSuccess(atEpochMs: Long) = samplerNotUsed()
    override suspend fun recordSubscriptionFailure(atEpochMs: Long, reason: String) = samplerNotUsed()
    override suspend fun setPerAppRoutes(routes: Map<String, AppRoute>) = samplerNotUsed()
}

private fun samplerNotUsed(): Nothing = throw NotImplementedError("not used by this test")
