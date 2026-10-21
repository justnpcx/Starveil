package com.justnpcx.starveil.feature.dashboard

import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.AppRoute
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.LatencySample
import com.justnpcx.starveil.core.model.LeakCheckRecord
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Scene
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drop policy, on its own.
 *
 * These were previously only reachable through the view model, which meant
 * "one retry per user action" could only be tested by staging a whole kernel
 * crash. The rule is the same one; it just has a name now.
 */
class DropRecoveryTest {

    private val tokyo = node(1, "Tokyo")
    private val osaka = node(2, "Osaka")

    private fun recovery(
        nodes: List<Profile> = listOf(tokyo, osaka),
        preferences: DropFakePrefs = DropFakePrefs(),
    ) = DropRecovery(DropFakeProfiles(nodes), DropNoHealth, preferences)

    @Test
    fun `a crash while disconnected is not a lost tunnel`() = runBlocking {
        val decision = recovery().decide(connected = false, busy = false)
        assertEquals(DropRecovery.Decision.Ignore, decision)
    }

    @Test
    fun `a crash during a connect is that attempt's business`() = runBlocking {
        val decision = recovery().decide(connected = true, busy = true)
        assertEquals(DropRecovery.Decision.Ignore, decision)
    }

    @Test
    fun `auto-reconnect off stops and says the tunnel dropped`() = runBlocking {
        val prefs = DropFakePrefs().apply { reconnect = false }
        val decision = recovery(preferences = prefs).decide(connected = true, busy = false)
        assertEquals(DropRecovery.Decision.GiveUp(DashboardNotice.TunnelDropped), decision)
    }

    @Test
    fun `only one automatic retry is granted per user action`() = runBlocking {
        val recovery = recovery(preferences = DropFakePrefs().apply { current = ConnectionTarget.Node(1) })

        assertTrue(recovery.decide(connected = true, busy = false) is DropRecovery.Decision.Reconnect)
        // The retry itself dies: no second attempt.
        assertEquals(
            DropRecovery.Decision.GiveUp(DashboardNotice.TunnelKeepsDropping),
            recovery.decide(connected = true, busy = false),
        )

        // A user action grants the next one.
        recovery.rearm()
        assertTrue(recovery.decide(connected = true, busy = false) is DropRecovery.Decision.Reconnect)
    }

    @Test
    fun `a single-node target is moved to a different node before the retry`() = runBlocking {
        val prefs = DropFakePrefs().apply { current = ConnectionTarget.Node(tokyo.id) }

        val decision = recovery(preferences = prefs).decide(connected = true, busy = false)

        val switched = (decision as DropRecovery.Decision.Reconnect).switchedTo
        assertEquals(osaka.id, switched?.profileId)
    }

    @Test
    fun `a group target keeps its selection and just reconnects`() = runBlocking {
        val prefs = DropFakePrefs().apply { current = ConnectionTarget.Group(9) }

        val decision = recovery(preferences = prefs).decide(connected = true, busy = false)

        assertEquals(DropRecovery.Decision.Reconnect(switchedTo = null), decision)
    }

    @Test
    fun `switching can be turned off without turning reconnecting off`() = runBlocking {
        val prefs = DropFakePrefs().apply {
            current = ConnectionTarget.Node(tokyo.id)
            switchOnDrop = false
        }

        val decision = recovery(preferences = prefs).decide(connected = true, busy = false)

        assertEquals(DropRecovery.Decision.Reconnect(switchedTo = null), decision)
    }

    private fun node(id: Long, name: String) = Profile(
        id = id,
        name = name,
        type = ProfileType.VLESS,
        server = "$name.example",
        port = 443,
        country = "JP",
    )
}

private class DropFakeProfiles(private val nodes: List<Profile>) : ProfileRepository {
    override fun observeAll(): Flow<List<Profile>> = flowOf(nodes)
    override fun observeByGroup(groupId: Long): Flow<List<Profile>> = flowOf(nodes.filter { it.groupId == groupId })
    override suspend fun getById(id: Long): Profile? = nodes.firstOrNull { it.id == id }
    override suspend fun upsert(profile: Profile): Long = dropNotUsed()
    override suspend fun deleteById(id: Long) = dropNotUsed()
    override suspend fun deleteByGroup(groupId: Long) = dropNotUsed()
    override suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>) = dropNotUsed()
}

private object DropNoHealth : NodeHealthRepository {
    override fun observeAll(): Flow<Map<Long, List<LatencySample>>> = flowOf(emptyMap())
    override suspend fun record(profileId: Long, latencyMs: Long, atMs: Long, weightTenths: Int) = dropNotUsed()
    override suspend fun forget(profileId: Long) = dropNotUsed()
}

/** Only the four signals the policy reads. */
private class DropFakePrefs : PreferencesRepository {

    override val proxyPrivateNetworks: Flow<Boolean> = MutableStateFlow(false)

    override suspend fun setProxyPrivateNetworks(enabled: Boolean) {
        (proxyPrivateNetworks as MutableStateFlow<Boolean>).value = enabled
    }
    var reconnect = true
    var switchOnDrop = true
    var current: ConnectionTarget = ConnectionTarget.Node(1)

    override val autoReconnect: Flow<Boolean> get() = flowOf(reconnect)
    override val autoSwitchOnDrop: Flow<Boolean> get() = flowOf(switchOnDrop)
    override val connectionTarget: Flow<ConnectionTarget> get() = flowOf(current)
    override val selectedProfileId: Flow<Long> = flowOf(1L)

    override val themeMode: Flow<ThemeMode> get() = dropNotUsed()
    override val dynamicColor: Flow<Boolean> get() = dropNotUsed()
    override val bypassMainland: Flow<Boolean> get() = dropNotUsed()
    override val routePreset: Flow<RoutePreset> get() = dropNotUsed()
    override val blockAds: Flow<Boolean> get() = dropNotUsed()
    override val subscribedRulePacks: Flow<Set<String>> get() = dropNotUsed()
    override val localApiEnabled: Flow<Boolean> get() = dropNotUsed()
    override val onboardingDone: Flow<Boolean> get() = dropNotUsed()
    override val killSwitchMode: Flow<KillSwitchMode> get() = dropNotUsed()
    override val selectedGroupId: Flow<Long> get() = dropNotUsed()
    override val lastLeakCheck: Flow<LeakCheckRecord> get() = dropNotUsed()
    override val scenes: Flow<List<Scene>> get() = dropNotUsed()
    override val manualPresetAt: Flow<Long> get() = dropNotUsed()
    override val lastHealthProbeAt: Flow<Long> get() = dropNotUsed()
    override val subscriptionLastSuccessAt: Flow<Long> get() = dropNotUsed()
    override val subscriptionLastFailureAt: Flow<Long> get() = dropNotUsed()
    override val subscriptionLastError: Flow<String?> get() = dropNotUsed()
    override val perAppRoutes: Flow<Map<String, AppRoute>> get() = dropNotUsed()

    override suspend fun setThemeMode(mode: ThemeMode) = dropNotUsed()
    override suspend fun setDynamicColor(enabled: Boolean) = dropNotUsed()
    override suspend fun setAutoReconnect(enabled: Boolean) = dropNotUsed()
    override suspend fun setAutoSwitchOnDrop(enabled: Boolean) = dropNotUsed()
    override suspend fun setBypassMainland(enabled: Boolean) = dropNotUsed()
    override suspend fun setRoutePreset(preset: RoutePreset) = dropNotUsed()
    override suspend fun setBlockAds(enabled: Boolean) = dropNotUsed()
    override suspend fun setRulePackSubscribed(id: String, subscribed: Boolean) = dropNotUsed()
    override suspend fun setLocalApiEnabled(enabled: Boolean) = dropNotUsed()
    override suspend fun setOnboardingDone(done: Boolean) = dropNotUsed()
    override suspend fun setKillSwitchMode(mode: KillSwitchMode) = dropNotUsed()
    override suspend fun setScenes(scenes: List<Scene>) = dropNotUsed()
    override suspend fun recordManualPresetChange(atEpochMs: Long) = dropNotUsed()
    override suspend fun recordHealthProbe(atEpochMs: Long) = dropNotUsed()
    override suspend fun recordSubscriptionSuccess(atEpochMs: Long) = dropNotUsed()
    override suspend fun recordSubscriptionFailure(atEpochMs: Long, reason: String) = dropNotUsed()
    override suspend fun setSelectedProfileId(id: Long) = dropNotUsed()
    override suspend fun setSelectedGroupId(id: Long) = dropNotUsed()
    override suspend fun setPerAppRoutes(routes: Map<String, AppRoute>) = dropNotUsed()
    override suspend fun recordLeakCheck(record: LeakCheckRecord) = dropNotUsed()
}

private fun dropNotUsed(): Nothing = throw NotImplementedError("not used by DropRecovery")
