package com.justnpcx.starveil.core.probe

import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.kernel.KernelLogLine
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.model.AppRoute
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Scene
import com.justnpcx.starveil.core.model.LeakCheckRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The leak self-check's decision.
 *
 * The inversion it guards is the one that matters: `trafficAllowed` is
 * *the absence of a tunnel*, because this app is excluded from its own tunnel
 * and so cannot observe a leak by making a connection.
 */
class LeakCheckRunnerTest {

    @Test
    fun `a blocking policy with no tunnel standing is a leak`() = runBlocking {
        val preferences = LeakFakePreferences(KillSwitchMode.BLOCK_ON_DISCONNECT)
        val runner = LeakCheckRunner(
            preferences = preferences,
            kernel = LeakFakeKernel(KernelState.Stopped),
            tunnel = TunnelProbe { false },
        )

        val record = runner.run()

        assertTrue("policy says block but nothing is holding traffic", record.leaking)
        assertEquals(record, preferences.lastRecord)
    }

    @Test
    fun `a blocking policy with the tunnel standing is clean`() = runBlocking {
        val runner = LeakCheckRunner(
            preferences = LeakFakePreferences(KillSwitchMode.BLOCK_ON_DISCONNECT),
            kernel = LeakFakeKernel(KernelState.Stopped),
            tunnel = TunnelProbe { true },
        )

        assertFalse(runner.run().leaking)
    }

    @Test
    fun `a blocking kernel holding the tun is clean, not a leak`() = runBlocking {
        // The block is a running kernel with a block-only config now. Calling
        // that "connected" would report a working Kill Switch as a leak.
        val runner = LeakCheckRunner(
            preferences = LeakFakePreferences(KillSwitchMode.BLOCK_ON_DISCONNECT),
            kernel = LeakFakeKernel(KernelState.Running, blockingNow = true),
            tunnel = TunnelProbe { true },
        )

        assertFalse(runner.run().leaking)
    }

    @Test
    fun `a blocking kernel whose tunnel died is a leak`() = runBlocking {
        // The kernel believes it is blocking, but nothing is holding the tun:
        // traffic leaves directly, which is exactly the failure to report.
        val runner = LeakCheckRunner(
            preferences = LeakFakePreferences(KillSwitchMode.BLOCK_ON_DISCONNECT),
            kernel = LeakFakeKernel(KernelState.Running, blockingNow = true),
            tunnel = TunnelProbe { false },
        )

        assertTrue(runner.run().leaking)
    }

    @Test
    fun `the kill switch off is never a leak`() = runBlocking {
        // No tunnel and traffic flowing freely is the intended state here.
        val runner = LeakCheckRunner(
            preferences = LeakFakePreferences(KillSwitchMode.OFF),
            kernel = LeakFakeKernel(KernelState.Stopped),
            tunnel = TunnelProbe { false },
        )

        assertFalse(runner.run().leaking)
    }

    @Test
    fun `connected with block-on-disconnect is allowed, tunnel or not`() = runBlocking {
        // The policy allows traffic while connected, so a missing tunnel is not
        // a leak — it is just not connected yet.
        val runner = LeakCheckRunner(
            preferences = LeakFakePreferences(KillSwitchMode.BLOCK_ON_DISCONNECT),
            kernel = LeakFakeKernel(KernelState.Running),
            tunnel = TunnelProbe { false },
        )

        assertFalse(runner.run().leaking)
    }

    @Test
    fun `the record is stamped so the screen can show when it ran`() = runBlocking {
        val runner = LeakCheckRunner(
            preferences = LeakFakePreferences(KillSwitchMode.OFF),
            kernel = LeakFakeKernel(KernelState.Stopped),
            tunnel = TunnelProbe { true },
        )

        assertTrue(runner.run().checkedAtEpochMs > 0L)
    }
}

private class LeakFakeKernel(initial: KernelState, blockingNow: Boolean = false) : KernelBridge {
    override val state: StateFlow<KernelState> = MutableStateFlow(initial)
    override val logs: SharedFlow<KernelLogLine> = MutableSharedFlow()
    override val connections: SharedFlow<KernelConnection> = MutableSharedFlow()
    override suspend fun start(config: String): Result<Unit> = Result.success(Unit)
    override suspend fun stop(): Result<Unit> = Result.success(Unit)
    override val blocking: StateFlow<Boolean> = MutableStateFlow(blockingNow)

    override suspend fun block(): Result<Unit> = Result.success(Unit)
    override suspend fun queryStats(): TrafficStats = TrafficStats()
    override suspend fun version(): String = "test"
}

private class LeakFakePreferences(private val mode: KillSwitchMode) : PreferencesRepository {

    var lastRecord: LeakCheckRecord? = null
        private set

    override val killSwitchMode: Flow<KillSwitchMode> = MutableStateFlow(mode)

    override suspend fun recordLeakCheck(record: LeakCheckRecord) {
        lastRecord = record
    }

    // The rest of the preferences are not what this test is about.
    override val selectedProfileId: Flow<Long> get() = leakNotUsed()
    override val selectedGroupId: Flow<Long> get() = leakNotUsed()
    override val connectionTarget: Flow<ConnectionTarget> get() = leakNotUsed()
    override val autoReconnect: Flow<Boolean> get() = leakNotUsed()
    override val autoSwitchOnDrop: Flow<Boolean> get() = leakNotUsed()
    override val bypassMainland: Flow<Boolean> get() = leakNotUsed()
    override val routePreset: Flow<RoutePreset> = flowOf(RoutePreset.GLOBAL_PROXY)
    override val blockAds: Flow<Boolean> = flowOf(false)
    override val subscribedRulePacks: Flow<Set<String>> = flowOf(emptySet())
    override val localApiEnabled: Flow<Boolean> = flowOf(false)
    override val onboardingDone: Flow<Boolean> = flowOf(true)
    override val lastLeakCheck: Flow<LeakCheckRecord> get() = leakNotUsed()
    override val scenes: Flow<List<Scene>> = flowOf(emptyList())
    override suspend fun setScenes(scenes: List<Scene>) { leakNotUsed() }
    override val manualPresetAt: Flow<Long> = flowOf(0L)
    override suspend fun recordManualPresetChange(atEpochMs: Long) { leakNotUsed() }
    override val lastHealthProbeAt: Flow<Long> get() = flowOf(0L)
    override suspend fun recordHealthProbe(atEpochMs: Long) { leakNotUsed() }
    override val subscriptionLastSuccessAt: Flow<Long> get() = leakNotUsed()
    override val subscriptionLastFailureAt: Flow<Long> get() = leakNotUsed()
    override val subscriptionLastError: Flow<String?> get() = leakNotUsed()
    override val perAppRoutes: Flow<Map<String, AppRoute>> get() = leakNotUsed()
    override suspend fun setDynamicColor(enabled: Boolean) { leakNotUsed() }
    override suspend fun setBypassMainland(enabled: Boolean) { leakNotUsed() }
    override suspend fun setRoutePreset(preset: RoutePreset) { leakNotUsed() }
    override suspend fun setBlockAds(enabled: Boolean) { leakNotUsed() }
    override suspend fun setRulePackSubscribed(id: String, subscribed: Boolean) { leakNotUsed() }
    override suspend fun setLocalApiEnabled(enabled: Boolean) { leakNotUsed() }
    override suspend fun setOnboardingDone(done: Boolean) { leakNotUsed() }
    override suspend fun setAutoReconnect(enabled: Boolean) { leakNotUsed() }
    override suspend fun setAutoSwitchOnDrop(enabled: Boolean) { leakNotUsed() }
    override suspend fun setKillSwitchMode(mode: KillSwitchMode) { leakNotUsed() }
    override suspend fun setSelectedProfileId(id: Long) { leakNotUsed() }
    override suspend fun setSelectedGroupId(id: Long) { leakNotUsed() }
    override suspend fun recordSubscriptionSuccess(atEpochMs: Long) { leakNotUsed() }
    override suspend fun recordSubscriptionFailure(atEpochMs: Long, reason: String) { leakNotUsed() }
    override suspend fun setPerAppRoutes(routes: Map<String, AppRoute>) { leakNotUsed() }
}

private fun leakNotUsed(): Nothing = throw NotImplementedError("not used by this test")
