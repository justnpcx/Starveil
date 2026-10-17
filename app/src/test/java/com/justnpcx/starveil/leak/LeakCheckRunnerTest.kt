package com.justnpcx.starveil.leak

import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.kernel.KernelLogLine
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.model.AppRoute
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.RoutePreset
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
        val preferences = FakePreferences(KillSwitchMode.BLOCK_ON_DISCONNECT)
        val runner = LeakCheckRunner(
            preferences = preferences,
            kernel = FakeKernel(KernelState.Stopped),
            tunnel = TunnelProbe { false },
        )

        val record = runner.run()

        assertTrue("policy says block but nothing is holding traffic", record.leaking)
        assertEquals(record, preferences.lastRecord)
    }

    @Test
    fun `a blocking policy with the tunnel standing is clean`() = runBlocking {
        val runner = LeakCheckRunner(
            preferences = FakePreferences(KillSwitchMode.BLOCK_ON_DISCONNECT),
            kernel = FakeKernel(KernelState.Stopped),
            tunnel = TunnelProbe { true },
        )

        assertFalse(runner.run().leaking)
    }

    @Test
    fun `the kill switch off is never a leak`() = runBlocking {
        // No tunnel and traffic flowing freely is the intended state here.
        val runner = LeakCheckRunner(
            preferences = FakePreferences(KillSwitchMode.OFF),
            kernel = FakeKernel(KernelState.Stopped),
            tunnel = TunnelProbe { false },
        )

        assertFalse(runner.run().leaking)
    }

    @Test
    fun `connected with block-on-disconnect is allowed, tunnel or not`() = runBlocking {
        // The policy allows traffic while connected, so a missing tunnel is not
        // a leak — it is just not connected yet.
        val runner = LeakCheckRunner(
            preferences = FakePreferences(KillSwitchMode.BLOCK_ON_DISCONNECT),
            kernel = FakeKernel(KernelState.Running),
            tunnel = TunnelProbe { false },
        )

        assertFalse(runner.run().leaking)
    }

    @Test
    fun `the record is stamped so the screen can show when it ran`() = runBlocking {
        val runner = LeakCheckRunner(
            preferences = FakePreferences(KillSwitchMode.OFF),
            kernel = FakeKernel(KernelState.Stopped),
            tunnel = TunnelProbe { true },
        )

        assertTrue(runner.run().checkedAtEpochMs > 0L)
    }
}

private class FakeKernel(initial: KernelState) : KernelBridge {
    override val state: StateFlow<KernelState> = MutableStateFlow(initial)
    override val logs: SharedFlow<KernelLogLine> = MutableSharedFlow()
    override val connections: SharedFlow<KernelConnection> = MutableSharedFlow()
    override suspend fun start(config: String): Result<Unit> = Result.success(Unit)
    override suspend fun stop(): Result<Unit> = Result.success(Unit)
    override suspend fun stopKeepingTunnel(): Result<Unit> = Result.success(Unit)
    override suspend fun queryStats(): TrafficStats = TrafficStats()
    override suspend fun version(): String = "test"
}

private class FakePreferences(private val mode: KillSwitchMode) : PreferencesRepository {

    var lastRecord: LeakCheckRecord? = null
        private set

    override val killSwitchMode: Flow<KillSwitchMode> = MutableStateFlow(mode)

    override suspend fun recordLeakCheck(record: LeakCheckRecord) {
        lastRecord = record
    }

    // The rest of the preferences are not what this test is about.
    override val selectedProfileId: Flow<Long> get() = notUsed()
    override val selectedGroupId: Flow<Long> get() = notUsed()
    override val connectionTarget: Flow<ConnectionTarget> get() = notUsed()
    override val themeMode: Flow<ThemeMode> get() = notUsed()
    override val dynamicColor: Flow<Boolean> get() = notUsed()
    override val autoReconnect: Flow<Boolean> get() = notUsed()
    override val autoSwitchOnDrop: Flow<Boolean> get() = notUsed()
    override val bypassMainland: Flow<Boolean> get() = notUsed()
    override val routePreset: Flow<RoutePreset> = flowOf(RoutePreset.GLOBAL_PROXY)
    override val blockAds: Flow<Boolean> = flowOf(false)
    override val lastLeakCheck: Flow<LeakCheckRecord> get() = notUsed()
    override val lastHealthProbeAt: Flow<Long> get() = flowOf(0L)
    override suspend fun recordHealthProbe(atEpochMs: Long) { notUsed() }
    override val subscriptionLastSuccessAt: Flow<Long> get() = notUsed()
    override val subscriptionLastFailureAt: Flow<Long> get() = notUsed()
    override val subscriptionLastError: Flow<String?> get() = notUsed()
    override val perAppRoutes: Flow<Map<String, AppRoute>> get() = notUsed()
    override suspend fun setThemeMode(mode: ThemeMode) { notUsed() }
    override suspend fun setDynamicColor(enabled: Boolean) { notUsed() }
    override suspend fun setBypassMainland(enabled: Boolean) { notUsed() }
    override suspend fun setRoutePreset(preset: RoutePreset) { notUsed() }
    override suspend fun setBlockAds(enabled: Boolean) { notUsed() }
    override suspend fun setAutoReconnect(enabled: Boolean) { notUsed() }
    override suspend fun setAutoSwitchOnDrop(enabled: Boolean) { notUsed() }
    override suspend fun setKillSwitchMode(mode: KillSwitchMode) { notUsed() }
    override suspend fun setSelectedProfileId(id: Long) { notUsed() }
    override suspend fun setSelectedGroupId(id: Long) { notUsed() }
    override suspend fun recordSubscriptionSuccess(atEpochMs: Long) { notUsed() }
    override suspend fun recordSubscriptionFailure(atEpochMs: Long, reason: String) { notUsed() }
    override suspend fun setPerAppRoutes(routes: Map<String, AppRoute>) { notUsed() }
}

private fun notUsed(): Nothing = throw NotImplementedError("not used by this test")
