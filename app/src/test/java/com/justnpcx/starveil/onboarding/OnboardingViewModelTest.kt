package com.justnpcx.starveil.onboarding

import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
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
import com.justnpcx.starveil.core.subscription.ImportFailure
import com.justnpcx.starveil.core.subscription.ImportOutcome
import com.justnpcx.starveil.core.subscription.SubscriptionFailure
import com.justnpcx.starveil.feature.dashboard.ConnectionOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The first-run flow, without the UI.
 *
 * What matters here is that onboarding is not a second path into the tunnel:
 * it calls the same importer, picks with the same [NodePicker], and writes the
 * same selection the dashboard would.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        nodes: List<Profile> = listOf(node(1, "Tokyo")),
        importOutcome: ImportOutcome = ImportOutcome.AddedNode("Tokyo"),
        connectOutcome: ConnectionOutcome = ConnectionOutcome.Connected("Tokyo"),
        preferences: FakePreferences = FakePreferences(),
    ): Triple<OnboardingViewModel, FakePreferences, FakeConnect> {
        val connect = FakeConnect(connectOutcome)
        val vm = OnboardingViewModel(
            importer = NodeImporter { importOutcome },
            profiles = FakeProfiles(nodes),
            nodeHealth = NoHealth,
            preferences = preferences,
            connectNow = connect,
        )
        return Triple(vm, preferences, connect)
    }

    @Test
    fun `a clipboard link is offered, ordinary text is not`() {
        val (vm, _, _) = viewModel()

        vm.detectClipboard("hello")
        assertNull(vm.state.value.detected)

        vm.detectClipboard("  vless://u@a.example:443#node  ")
        assertEquals("vless://u@a.example:443#node", vm.state.value.detected)
    }

    @Test
    fun `a successful import moves to the connect step`() = runBlocking {
        val (vm, _, _) = viewModel(
            importOutcome = ImportOutcome.AddedSubscription(nodeCount = 3, dropped = 1),
        )

        vm.import("https://sub.example/x")
        yield()

        assertEquals(2, vm.state.value.step)
        assertEquals(ImportResult.Imported(3, 1), vm.state.value.importResult)
        assertFalse(vm.state.value.importing)
    }

    @Test
    fun `a failed import stays on the import step and says why`() = runBlocking {
        val failure = ImportFailure.Subscription(SubscriptionFailure.NoUsableNodes)
        val (vm, _, _) = viewModel(importOutcome = ImportOutcome.Failed(failure))
        vm.next()

        vm.import("not a link")
        yield()

        assertEquals(1, vm.state.value.step)
        assertEquals(ImportResult.Failed(failure), vm.state.value.importResult)
    }

    @Test
    fun `connecting selects the picker's node before it dials`() = runBlocking {
        val (vm, preferences, connect) = viewModel()

        vm.connectBest()
        yield()

        assertEquals(1L, preferences.selected)
        assertEquals(1, connect.calls)
        assertEquals(ConnectResult.Connected("Tokyo"), vm.state.value.connectResult)
    }

    @Test
    fun `no nodes is reported without attempting a connection`() = runBlocking {
        val (vm, _, connect) = viewModel(nodes = emptyList())

        vm.connectBest()
        yield()

        assertEquals(ConnectResult.NoNodes, vm.state.value.connectResult)
        assertEquals(0, connect.calls)
    }

    @Test
    fun `a refused connection carries the redacted reason`() = runBlocking {
        val (vm, _, _) = viewModel(connectOutcome = ConnectionOutcome.Refused("start failed"))

        vm.connectBest()
        yield()

        assertEquals(ConnectResult.Failed("start failed"), vm.state.value.connectResult)
    }

    @Test
    fun `finish writes the flag, so the screen never returns`() = runBlocking {
        val (vm, preferences, _) = viewModel()

        vm.finish()
        yield()

        assertTrue(preferences.done)
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

private class FakeConnect(private val outcome: ConnectionOutcome) : ConnectNow {
    var calls = 0
        private set

    override suspend fun connect(): ConnectionOutcome {
        calls++
        return outcome
    }
}

private class FakeProfiles(private val nodes: List<Profile>) : ProfileRepository {
    override fun observeAll(): Flow<List<Profile>> = flowOf(nodes)
    override fun observeByGroup(groupId: Long): Flow<List<Profile>> = flowOf(nodes.filter { it.groupId == groupId })
    override suspend fun getById(id: Long): Profile? = nodes.firstOrNull { it.id == id }
    override suspend fun upsert(profile: Profile): Long = notUsed()
    override suspend fun deleteById(id: Long) = notUsed()
    override suspend fun deleteByGroup(groupId: Long) = notUsed()
    override suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>) = notUsed()
}

private object NoHealth : NodeHealthRepository {
    override fun observeAll(): Flow<Map<Long, List<LatencySample>>> = flowOf(emptyMap())
    override suspend fun record(profileId: Long, latencyMs: Long, atMs: Long, weightTenths: Int) = notUsed()
    override suspend fun forget(profileId: Long) = notUsed()
}

/** Only the selection and the flag matter to onboarding. */
private class FakePreferences : PreferencesRepository {
    var selected = 0L
        private set
    var done = false
        private set

    override val selectedProfileId: Flow<Long> = MutableStateFlow(0L)
    override val onboardingDone: Flow<Boolean> = flowOf(false)

    override suspend fun setSelectedProfileId(id: Long) {
        selected = id
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        this.done = done
    }

    override val autoReconnect: Flow<Boolean> get() = notUsed()
    override val autoSwitchOnDrop: Flow<Boolean> get() = notUsed()
    override val bypassMainland: Flow<Boolean> get() = notUsed()
    override val routePreset: Flow<RoutePreset> = flowOf(RoutePreset.GLOBAL_PROXY)
    override val blockAds: Flow<Boolean> = flowOf(false)
    override val subscribedRulePacks: Flow<Set<String>> = flowOf(emptySet())
    override val localApiEnabled: Flow<Boolean> = flowOf(false)
    override val dnsMetricsEnabled: Flow<Boolean> = flowOf(true)
    override val killSwitchMode: Flow<KillSwitchMode> get() = notUsed()
    override val selectedGroupId: Flow<Long> get() = notUsed()
    override val connectionTarget: Flow<ConnectionTarget> get() = notUsed()
    override val lastLeakCheck: Flow<LeakCheckRecord> get() = notUsed()
    override val scenes: Flow<List<Scene>> = flowOf(emptyList())
    override val manualPresetAt: Flow<Long> = flowOf(0L)
    override val lastHealthProbeAt: Flow<Long> = flowOf(0L)
    override val subscriptionLastSuccessAt: Flow<Long> get() = notUsed()
    override val subscriptionLastFailureAt: Flow<Long> get() = notUsed()
    override val subscriptionLastError: Flow<String?> get() = notUsed()
    override val perAppRoutes: Flow<Map<String, AppRoute>> get() = notUsed()

    override suspend fun setAutoReconnect(enabled: Boolean) = notUsed()
    override suspend fun setAutoSwitchOnDrop(enabled: Boolean) = notUsed()
    override suspend fun setBypassMainland(enabled: Boolean) = notUsed()
    override suspend fun setRoutePreset(preset: RoutePreset) = notUsed()
    override suspend fun setBlockAds(enabled: Boolean) = notUsed()
    override suspend fun setRulePackSubscribed(id: String, subscribed: Boolean) = notUsed()
    override suspend fun setLocalApiEnabled(enabled: Boolean) = notUsed()
    override suspend fun setDnsMetricsEnabled(enabled: Boolean) {}
    override suspend fun setKillSwitchMode(mode: KillSwitchMode) = notUsed()
    override suspend fun setScenes(scenes: List<Scene>) = notUsed()
    override suspend fun recordManualPresetChange(atEpochMs: Long) = notUsed()
    override suspend fun recordHealthProbe(atEpochMs: Long) = notUsed()
    override suspend fun recordSubscriptionSuccess(atEpochMs: Long) = notUsed()
    override suspend fun recordSubscriptionFailure(atEpochMs: Long, reason: String) = notUsed()
    override suspend fun setSelectedGroupId(id: Long) = notUsed()
    override suspend fun setPerAppRoutes(routes: Map<String, AppRoute>) = notUsed()
    override suspend fun recordLeakCheck(record: LeakCheckRecord) = notUsed()
}

private fun notUsed(): Nothing = throw NotImplementedError("not used by onboarding")
