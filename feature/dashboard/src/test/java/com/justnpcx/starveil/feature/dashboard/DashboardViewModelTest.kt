package com.justnpcx.starveil.feature.dashboard

import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.crypto.LocalApiTokenStore
import com.justnpcx.starveil.core.data.ruleset.MainlandRuleSetStore
import com.justnpcx.starveil.core.data.ruleset.RulePackFailure
import com.justnpcx.starveil.core.data.ruleset.RulePackStore
import com.justnpcx.starveil.core.data.ruleset.RuleSetFailure
import com.justnpcx.starveil.core.model.InstalledRulePack
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.data.scene.SceneApplyState
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelError
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.kernel.KernelLogLine
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
import com.justnpcx.starveil.core.kernel.timing.ConnectClock
import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.AppRoute
import com.justnpcx.starveil.core.model.CountryFlags
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Scene
import com.justnpcx.starveil.core.model.LeakCheckRecord
import com.justnpcx.starveil.core.model.NodeRegion
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.VlessSettings
import com.justnpcx.starveil.core.ui.components.ConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.justnpcx.starveil.core.model.LatencySample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The kernel runs whatever config was built when the tunnel started, so a node
 * switch has to be applied to it — otherwise the dashboard names a node that is
 * not carrying the traffic, and every connection fails silently against the old
 * one. That is what happened on device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `switching node while connected restarts the kernel on the new node`() = runBlocking {
        val preferences = FakePreferences(ONE)
        val kernel = RecordingKernel()
        val viewModel = viewModel(preferences, kernel)

        viewModel.onCoreClick()
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)
        assertEquals(1, kernel.starts)
        assertTrue(kernel.lastConfig.orEmpty().contains("\"server\":\"one.example\""))

        preferences.setSelectedProfileId(TWO)

        assertEquals("two", viewModel.uiState.value.selectedNodeName)
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)
        assertEquals(2, kernel.starts)
        assertTrue(kernel.lastConfig.orEmpty().contains("\"server\":\"two.example\""))
    }

    @Test
    fun `the node label keeps its flag when another setting changes`() = runBlocking {
        val preferences = FakePreferences(ONE)
        val viewModel = viewModel(
            preferences,
            RecordingKernel(),
            nodes = listOf(profile(ONE, "one", country = "US"), profile(TWO, "two")),
        )

        viewModel.onCoreClick()
        assertEquals("${CountryFlags.flag("US")} one", viewModel.uiState.value.selectedNodeName)

        // Any write re-reads the store, which re-emits the target even though
        // the target did not change. The label is rebuilt from the profile, and
        // on device it came back without the flag — the node tab still had one.
        preferences.setBlockAds(true)

        assertEquals("${CountryFlags.flag("US")} one", viewModel.uiState.value.selectedNodeName)
    }

    @Test
    fun `changing the preset while connected restarts the kernel on it`() = runBlocking {
        val preferences = FakePreferences(ONE)
        val kernel = RecordingKernel()
        val viewModel = viewModel(preferences, kernel)

        viewModel.onCoreClick()
        assertEquals(1, kernel.starts)

        // The policy is part of the config, so a tunnel already up is running
        // the old one until it is rebuilt.
        preferences.setRoutePreset(RoutePreset.GLOBAL_DIRECT)

        assertEquals(2, kernel.starts)
        assertTrue(kernel.lastConfig.orEmpty().contains("\"final\":\"direct\""))
    }

    @Test
    fun `subscribing does not restart the kernel`() = runBlocking {
        val kernel = RecordingKernel()

        viewModel(FakePreferences(ONE), kernel)

        // The flow replays the current selection on subscribe; that is not a
        // switch, so nothing should have been started.
        assertEquals(0, kernel.starts)
    }

    @Test
    fun `switching node while disconnected starts nothing`() = runBlocking {
        val preferences = FakePreferences(ONE)
        val kernel = RecordingKernel()
        val viewModel = viewModel(preferences, kernel)

        preferences.setSelectedProfileId(TWO)

        assertEquals("two", viewModel.uiState.value.selectedNodeName)
        assertEquals(0, kernel.starts)
    }

    @Test
    fun `always block starts a reject-everything config instead of the node`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(FakePreferences(ONE, KillSwitchMode.ALWAYS_BLOCK), kernel)

        viewModel.onCoreClick()

        assertEquals(ConnectionState.BLOCKED, viewModel.uiState.value.connectionState)
        // The node is never built: the block config has no outbound but
        // `block`, which is asserted where that config is built.
        assertEquals(0, kernel.starts)
        assertEquals(1, kernel.blocks)
        assertTrue(kernel.blocking.value)
    }

    @Test
    fun `always block that cannot start is disconnected, not blocked`() = runBlocking {
        val kernel = RecordingKernel().apply { blockFails = true }
        val viewModel = viewModel(FakePreferences(ONE, KillSwitchMode.ALWAYS_BLOCK), kernel)

        viewModel.onCoreClick()

        // The block was asked for but nothing enforces it, so the honest
        // state is "not connected" — the same rule disconnect() follows.
        assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.connectionState)
        assertEquals(DashboardNotice.KillSwitchFailed, viewModel.uiState.value.notice)
        assertEquals(false, kernel.blocking.value)
    }

    @Test
    fun `a connect the kernel refuses is disconnected, not blocked`() = runBlocking {
        val kernel = RecordingKernel().apply { refuseOnStart = true }
        val viewModel = viewModel(
            FakePreferences(ONE, KillSwitchMode.OFF, autoReconnect = false),
            kernel,
        )

        viewModel.onCoreClick()
        yield()

        assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.connectionState)
        assertTrue(viewModel.uiState.value.notice is DashboardNotice.KernelRefused)
    }

    @Test
    fun `block on disconnect swaps in a kernel that forwards nothing`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(FakePreferences(ONE, KillSwitchMode.BLOCK_ON_DISCONNECT), kernel)

        viewModel.onCoreClick() // connect
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)

        viewModel.onCoreClick() // disconnect
        // Tearing the tunnel down here is the leak this mode exists to stop.
        assertEquals(ConnectionState.BLOCKED, viewModel.uiState.value.connectionState)
        assertEquals(1, kernel.blocks)
        assertTrue(kernel.blocking.value)
    }

    @Test
    fun `a drop switches to another node when the switch is on`() = runBlocking {
        val kernel = RecordingKernel()
        val preferences = FakePreferences(ONE, switchOnDrop = true)
        val viewModel = viewModel(preferences, kernel)

        viewModel.onCoreClick()
        assertEquals(ONE, preferences.selectedProfileId.first())

        kernel.crash()
        yield()

        // Retrying the node that just died is the least useful thing to do
        // with a drop, so the selection moved.
        assertEquals(TWO, preferences.selectedProfileId.first())
        assertEquals(2, kernel.starts)
    }

    @Test
    fun `with the switch off a drop retries the same node`() = runBlocking {
        val kernel = RecordingKernel()
        val preferences = FakePreferences(ONE, switchOnDrop = false)
        val viewModel = viewModel(preferences, kernel)

        viewModel.onCoreClick()
        kernel.crash()
        yield()

        assertEquals(ONE, preferences.selectedProfileId.first())
        assertEquals(2, kernel.starts)
    }

    @Test
    fun `a connect from outside the dashboard is reflected in the ui`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(FakePreferences(ONE), kernel)
        assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.connectionState)

        // The quick-settings tile: the kernel comes up and this view model
        // never hears about it through its own code path.
        kernel.start("{}")
        yield()

        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)
        assertEquals("one", viewModel.uiState.value.selectedNodeName)
    }

    @Test
    fun `a stop from outside the dashboard is reflected in the ui`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(FakePreferences(ONE, KillSwitchMode.OFF), kernel)
        viewModel.onCoreClick()
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)

        // The notification's Disconnect: the kernel stops and this view model
        // never hears about it through its own code path.
        kernel.stop()
        yield()

        assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.connectionState)
    }

    @Test
    fun `a raw stop from outside leaves no block, so it is disconnected`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(
            FakePreferences(ONE, KillSwitchMode.BLOCK_ON_DISCONNECT),
            kernel,
        )
        viewModel.onCoreClick()

        // A bare stop, not the notification's Disconnect (which goes through
        // ConnectionController and therefore applies the policy). Nothing is
        // dropping packets here, so BLOCKED would be a claim about traffic
        // that nothing enforces.
        kernel.stop()
        yield()

        assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.connectionState)
    }

    @Test
    fun `a blackhole left by an outside disconnect is blocked`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(FakePreferences(ONE), kernel)
        viewModel.onCoreClick()

        // What ConnectionController.disconnect() does under the policy: the
        // kernel stops, the tun stays, and the bridge reports blocking.
        kernel.stopKeepingTunnel()
        yield()

        assertEquals(ConnectionState.BLOCKED, viewModel.uiState.value.connectionState)
    }

    @Test
    fun `with the kill switch off, disconnecting really disconnects`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(FakePreferences(ONE, KillSwitchMode.OFF), kernel)

        viewModel.onCoreClick()
        viewModel.onCoreClick()

        assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.connectionState)
        assertEquals(0, kernel.blocks)
    }

    @Test
    fun `a dropped tunnel is reconnected when auto-reconnect is on`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(FakePreferences(ONE, autoReconnect = true), kernel)

        viewModel.onCoreClick()
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)
        assertEquals(1, kernel.starts)

        kernel.crash()

        // The kernel died under a believed-connected dashboard; bring it back
        // rather than leaving a stale CONNECTED label over a dead tunnel.
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)
        assertEquals(2, kernel.starts)
    }

    @Test
    fun `a rebuild re-arms the one-shot auto-reconnect`() = runBlocking {
        val kernel = RecordingKernel()
        val preferences = FakePreferences(ONE, autoReconnect = true, switchOnDrop = false)
        val viewModel = viewModel(preferences, kernel)

        viewModel.onCoreClick()
        assertEquals(1, kernel.starts)

        // The drop spends the one retry.
        kernel.crash()
        assertEquals(2, kernel.starts)
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)

        // A rebuild is the user's policy change, not a retry: it has to leave
        // the retry armed, or one early drop would disable recovery for the
        // rest of the session.
        preferences.setRoutePreset(RoutePreset.GLOBAL_DIRECT)
        assertEquals(3, kernel.starts)

        kernel.crash()
        assertEquals(
            "the rebuild must have re-armed the retry",
            4,
            kernel.starts,
        )
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)
    }

    @Test
    fun `a dropped tunnel stays down when auto-reconnect is off`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(FakePreferences(ONE, autoReconnect = false), kernel)

        viewModel.onCoreClick()
        kernel.crash()

        assertEquals(ConnectionState.BLOCKED, viewModel.uiState.value.connectionState)
        assertEquals(1, kernel.starts)
    }

    @Test
    fun `a second drop after an auto-reconnect is not retried`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(FakePreferences(ONE, autoReconnect = true), kernel)

        viewModel.onCoreClick() // starts = 1
        kernel.crash() // one auto-reconnect -> starts = 2
        assertEquals(2, kernel.starts)

        kernel.crash() // budget spent: give up rather than loop

        assertEquals(2, kernel.starts)
        assertEquals(ConnectionState.BLOCKED, viewModel.uiState.value.connectionState)
    }

    @Test
    fun `a user connect re-arms the auto-reconnect`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(FakePreferences(ONE, autoReconnect = true), kernel)

        viewModel.onCoreClick() // starts = 1
        kernel.crash() // auto-reconnect -> starts = 2, budget spent
        assertEquals(2, kernel.starts)

        viewModel.onCoreClick() // user disconnects
        viewModel.onCoreClick() // user connects again -> starts = 3, budget re-armed
        assertEquals(3, kernel.starts)

        kernel.crash() // a fresh drop gets its own attempt -> starts = 4

        assertEquals(4, kernel.starts)
    }

    @Test
    fun `a crash while disconnected is not a drop to recover from`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(FakePreferences(ONE, autoReconnect = true), kernel)

        // Never connected: the UI is DISCONNECTED, so a kernel error is not a
        // lost tunnel and must not start one.
        kernel.crash()

        assertEquals(0, kernel.starts)
        assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.connectionState)
    }

    @Test
    fun `connecting through a urltest group builds the group config`() = runBlocking {
        val kernel = RecordingKernel()
        val preferences = FakePreferences(0L)
        val groups = FakeGroups(listOf(group(SUB, GroupMode.URL_TEST, listOf(ONE, TWO))))
        preferences.setSelectedGroupId(SUB)
        val viewModel = viewModel(preferences, kernel, groups)

        viewModel.onCoreClick()

        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)
        assertEquals(1, kernel.starts)
        val config = kernel.lastConfig.orEmpty()
        assertTrue(config.contains("\"type\":\"urltest\""))
        // Both members are in the config, so the kernel has something to pick
        // between — that is the whole point of connecting through a group.
        assertTrue(config.contains("one.example"))
        assertTrue(config.contains("two.example"))
        assertEquals("sub", viewModel.uiState.value.selectedNodeName)
    }

    @Test
    fun `connecting through a selector group emits a selector`() = runBlocking {
        val kernel = RecordingKernel()
        val preferences = FakePreferences(0L)
        val groups = FakeGroups(listOf(group(SUB, GroupMode.SELECTOR, listOf(ONE, TWO))))
        preferences.setSelectedGroupId(SUB)
        val viewModel = viewModel(preferences, kernel, groups)

        viewModel.onCoreClick()

        assertEquals(1, kernel.starts)
        assertTrue(kernel.lastConfig.orEmpty().contains("\"type\":\"selector\""))
    }

    @Test
    fun `a manual group is not a connection target`() = runBlocking {
        // A manual group is a folder. Connecting "through" it is meaningless,
        // so it must be refused rather than quietly connecting to a member.
        val kernel = RecordingKernel()
        val preferences = FakePreferences(0L)
        val groups = FakeGroups(listOf(group(SUB, GroupMode.MANUAL, listOf(ONE))))
        preferences.setSelectedGroupId(SUB)
        val viewModel = viewModel(preferences, kernel, groups)

        viewModel.onCoreClick()

        assertEquals(0, kernel.starts)
        assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.connectionState)
        assertEquals(DashboardNotice.NoTarget, viewModel.uiState.value.notice)
    }

    @Test
    fun `a group with no members left is not a connection target`() = runBlocking {
        val kernel = RecordingKernel()
        val preferences = FakePreferences(0L)
        val groups = FakeGroups(listOf(group(SUB, GroupMode.URL_TEST, emptyList())))
        preferences.setSelectedGroupId(SUB)
        val viewModel = viewModel(preferences, kernel, groups)

        viewModel.onCoreClick()

        assertEquals(0, kernel.starts)
        assertEquals(DashboardNotice.NoTarget, viewModel.uiState.value.notice)
    }

    @Test
    fun `switching from a node to a group while connected reconnects exactly once`() = runBlocking {
        // The node and the group are one stored choice, so this is a single
        // change of target. Watching them as two flows used to make it two,
        // tearing the tunnel down twice.
        val kernel = RecordingKernel()
        val preferences = FakePreferences(ONE)
        val groups = FakeGroups(listOf(group(SUB, GroupMode.URL_TEST, listOf(ONE, TWO))))
        val viewModel = viewModel(preferences, kernel, groups)

        viewModel.onCoreClick()
        assertEquals(1, kernel.starts)
        assertTrue(kernel.lastConfig.orEmpty().contains("\"server\":\"one.example\""))

        preferences.setSelectedGroupId(SUB)

        assertEquals(2, kernel.starts)
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)
        assertTrue(kernel.lastConfig.orEmpty().contains("\"type\":\"urltest\""))
        assertEquals("sub", viewModel.uiState.value.selectedNodeName)
    }

    @Test
    fun `a leak found while connected turns the core into a warning`() = runBlocking {
        val preferences = FakePreferences(ONE)
        val viewModel = viewModel(preferences, RecordingKernel())
        viewModel.onCoreClick()
        yield()
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.coreState)

        preferences.reportLeak(LeakCheckRecord(checkedAtEpochMs = 1L, leaking = true))
        yield()

        // The tunnel is still up and still carrying traffic: this is a warning,
        // not a failure, so the underlying state must not change.
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)
        assertEquals(ConnectionState.WARNING, viewModel.uiState.value.coreState)
    }

    @Test
    fun `a leak only warns while there is a tunnel to warn about`() {
        // Pure mapping — no tunnel up means nothing to be warned about, however
        // stale the last self-check is.
        val leaking = DashboardUiState(leakDetected = true)

        assertEquals(ConnectionState.DISCONNECTED, leaking.coreState)
        assertEquals(
            ConnectionState.WARNING,
            leaking.copy(connectionState = ConnectionState.CONNECTED).coreState,
        )
        assertEquals(
            ConnectionState.BLOCKED,
            leaking.copy(connectionState = ConnectionState.BLOCKED).coreState,
        )
        assertEquals(
            ConnectionState.CONNECTING,
            leaking.copy(connectionState = ConnectionState.CONNECTING).coreState,
        )
    }

    @Test
    fun `an automatic reconnect says why it is retrying`() = runBlocking {
        val kernel = RecordingKernel()
        // Node switching off, so the notice is the plain retry one. The
        // switching path has its own notice and its own test.
        val viewModel = viewModel(
            FakePreferences(ONE, autoReconnect = true, switchOnDrop = false),
            kernel,
        )
        viewModel.onCoreClick()
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)

        // Hold the retry open so the in-flight state can be inspected; in
        // production that window is however long the kernel takes to start.
        kernel.gate = CompletableDeferred()
        kernel.crash()
        yield()

        assertEquals(ConnectionState.CONNECTING, viewModel.uiState.value.connectionState)
        val notice = viewModel.uiState.value.notice
        // Setting the notice and then calling connect() used to wipe it on the
        // next line, so the user saw a core spinning with no explanation.
        // The notice is now data, so this asserts the *reason* rather than
        // grepping the sentence for a word — which is what the old string
        // version of this test had to do.
        assertEquals(DashboardNotice.TunnelReconnecting, notice)

        kernel.gate!!.complete(Unit)
        yield()
        // And it must not outlive the attempt it described.
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun `the dashboard carries the selected node's health`() = runBlocking {
        // §7.2's curve is about a node, so the dashboard reads the same history
        // the Nodes tab draws its card from.
        val samples = listOf(
            LatencySample(atMs = 1_000L, latencyMs = 100),
            LatencySample(atMs = 2_000L, latencyMs = 200),
        )
        val viewModel = viewModel(
            preferences = FakePreferences(ONE),
            kernel = RecordingKernel(),
            nodeHealth = FakeNodeHealth(mapOf(ONE to samples)),
        )
        yield()

        val health = viewModel.uiState.value.health
        assertNotNull("expected the selected node's health", health)
        assertEquals(2, health!!.samples.size)
    }

    @Test
    fun `a node with no history is collecting, not absent`() = runBlocking {
        // "Nothing measured yet" and "no curve at all" are different, and the
        // chart says the first in words. Returning null here would leave the
        // area blank instead.
        val viewModel = viewModel(
            preferences = FakePreferences(ONE),
            kernel = RecordingKernel(),
            nodeHealth = FakeNodeHealth(emptyMap()),
        )
        yield()

        val health = viewModel.uiState.value.health
        assertNotNull(health)
        assertTrue(health!!.collecting)
    }

    @Test
    fun `a group target has no curve of its own`() = runBlocking {
        val preferences = FakePreferences(ONE)
        val viewModel = viewModel(preferences, RecordingKernel())
        yield()

        preferences.setSelectedGroupId(GROUP_ID)
        yield()

        assertNull(viewModel.uiState.value.health)
    }

    @Test
    fun `a stale selection has no curve`() = runBlocking {
        // A subscription refresh replaces a group's nodes with new ids, so the
        // selection can point at a node that is gone while its samples are
        // still in the window. Drawing those would describe something that is
        // gone — and it did, on device.
        val samples = listOf(LatencySample(atMs = 1_000L, latencyMs = 100))
        val viewModel = viewModel(
            preferences = FakePreferences(STALE_ID),
            kernel = RecordingKernel(),
            nodeHealth = FakeNodeHealth(mapOf(STALE_ID to samples)),
        )
        yield()

        assertNull(viewModel.uiState.value.health)
    }

    private fun viewModel(
        preferences: FakePreferences,
        kernel: RecordingKernel,
        groups: FakeGroups = FakeGroups(emptyList()),
        nodeHealth: NodeHealthRepository = FakeNodeHealth(),
        nodes: List<Profile> = listOf(profile(ONE, "one"), profile(TWO, "two")),
    ): DashboardViewModel {
        val profiles = FakeProfiles(nodes)
        // The view model delegates the connect path to this; giving both the
        // same fakes is what makes the tests exercise the real decision.
        val connection = ConnectionController(
            kernel = kernel,
            profiles = profiles,
            groups = groups,
            rules = FakeRules(),
            preferences = preferences,
            configBuilder = ConfigBuilder(ProtocolRegistry.default()),
            mainlandRuleSets = mainlandRuleSets,
            rulePackStore = NoRulePacks,
            localApiToken = FakeTokens,
            timing = ConnectClock { },
            localSubnets = NoLocalSubnets,
        ).apply {
            // Config building hops threads in production; in a test that
            // asserts immediately after connect(), that hop is a suspension it
            // cannot see past.
            configDispatcher = Dispatchers.Unconfined
        }
        return DashboardViewModel(
            kernel = kernel,
            profiles = profiles,
            preferences = preferences,
            groups = groups,
            connection = connection,
            nodeHealth = nodeHealth,
            recovery = DropRecovery(profiles, nodeHealth, preferences),
            sceneApply = SceneApplyState(),
        )
    }

    /**
     * No rule set files. The preset is off in these tests, so nothing asks for
     * them; a test that turns it on would provide paths here.
     */
    private val mainlandRuleSets = object : MainlandRuleSetStore {
        override suspend fun ensure() = null
        override suspend fun ensureAds(): String? = null
        override val failure: StateFlow<RuleSetFailure?> = MutableStateFlow(null)
    }

    private object FakeTokens : LocalApiTokenStore {
        override fun token(): String = "test-token"
        override fun reset(): String = "test-token"
    }

    private object NoRulePacks : RulePackStore {
        override suspend fun ensure(ids: Set<String>) = emptyList<InstalledRulePack>()
        override val failure: StateFlow<RulePackFailure?> = MutableStateFlow(null)
    }

    private fun profile(id: Long, name: String, country: String? = null) = Profile(
        id = id,
        name = name,
        type = ProfileType.VLESS,
        server = "$name.example",
        port = 443,
        settings = VlessSettings(uuid = "11111111-2222-3333-4444-555555555555"),
        country = country,
    )

    private fun group(id: Long, mode: GroupMode, members: List<Long>) =
        ProxyGroup(id = id, name = "sub", mode = mode, profileIds = members)

    private companion object {
        const val ONE = 1L
        const val TWO = 2L
        const val GROUP_ID = 9L

        /** Not in the fake node list — a node a refresh replaced. */
        const val STALE_ID = 99L
        const val SUB = 7L
    }
}

private class FakePreferences(
    initialId: Long,
    initialKillSwitch: KillSwitchMode = KillSwitchMode.OFF,
    autoReconnect: Boolean = true,
    switchOnDrop: Boolean = true,
) : PreferencesRepository {

    private val killSwitch = MutableStateFlow(initialKillSwitch)
    private val autoReconnectFlow = MutableStateFlow(autoReconnect)
    private val leak = MutableStateFlow(LeakCheckRecord())

    /** Stands in for the self-check writing its verdict. */
    fun reportLeak(record: LeakCheckRecord) {
        leak.value = record
    }

    private val target = MutableStateFlow<ConnectionTarget>(
        if (initialId != 0L) ConnectionTarget.Node(initialId) else ConnectionTarget.None,
    )

    /**
     * Bumped by every write. DataStore re-reads the whole file on any edit, so
     * every derived flow re-emits — including one whose value did not change.
     * Without this the fake is quieter than the real repository and the bug
     * that made the flag vanish after an unrelated setting is invisible here.
     */
    private val write = MutableStateFlow(0)

    private fun wrote() {
        write.value += 1
    }

    override val selectedProfileId: Flow<Long> =
        target.map { (it as? ConnectionTarget.Node)?.id ?: 0L }
    override val selectedGroupId: Flow<Long> =
        target.map { (it as? ConnectionTarget.Group)?.id ?: 0L }
    override val connectionTarget: Flow<ConnectionTarget> =
        combine(target, write) { t, _ -> t }

    override val killSwitchMode: Flow<KillSwitchMode> = killSwitch
    override val autoReconnect: Flow<Boolean> = autoReconnectFlow
    // Real: the drop handler reads it before deciding whether to switch.
    override val autoSwitchOnDrop: Flow<Boolean> = flowOf(switchOnDrop)
    // Off, like the app's default: a test that wants the preset on says so.
    override val bypassMainland: Flow<Boolean> = flowOf(false)
    private val preset = MutableStateFlow(RoutePreset.GLOBAL_PROXY)
    private val ads = MutableStateFlow(false)
    override val routePreset: Flow<RoutePreset> = preset
    override val blockAds: Flow<Boolean> = ads
    private val rulePacks = MutableStateFlow<Set<String>>(emptySet())
    override val subscribedRulePacks: Flow<Set<String>> = rulePacks
    override val localApiEnabled: Flow<Boolean> = flowOf(false)
    override val dnsMetricsEnabled: Flow<Boolean> = flowOf(true)
    override val onboardingDone: Flow<Boolean> = flowOf(true)
    // Real rather than notUsed(): the connect path reads it on every build.
    private val perAppRoutesFlow = MutableStateFlow<Map<String, AppRoute>>(emptyMap())
    override val perAppRoutes: Flow<Map<String, AppRoute>> = perAppRoutesFlow
    override val lastLeakCheck: Flow<LeakCheckRecord> = leak

    override suspend fun setSelectedProfileId(id: Long) {
        // Clearing the node must not disturb a group that is selected instead;
        // the real repository only touches its own key.
        target.value = when {
            id != 0L -> ConnectionTarget.Node(id)
            target.value is ConnectionTarget.Node -> ConnectionTarget.None
            else -> target.value
        }
        wrote()
    }

    override suspend fun setSelectedGroupId(id: Long) {
        target.value = when {
            id != 0L -> ConnectionTarget.Group(id)
            target.value is ConnectionTarget.Group -> ConnectionTarget.None
            else -> target.value
        }
        wrote()
    }

    // The rest of the preferences are not what this test is about.
    override val scenes: Flow<List<Scene>> = flowOf(emptyList())
    override suspend fun setScenes(scenes: List<Scene>) = notUsed()
    override val manualPresetAt: Flow<Long> = flowOf(0L)
    override suspend fun recordManualPresetChange(atEpochMs: Long) = notUsed()
    override val lastHealthProbeAt: Flow<Long> get() = flowOf(0L)
    override suspend fun recordHealthProbe(atEpochMs: Long) { notUsed() }
    override val subscriptionLastSuccessAt: Flow<Long> get() = notUsed()
    override val subscriptionLastFailureAt: Flow<Long> get() = notUsed()
    override val subscriptionLastError: Flow<String?> get() = notUsed()
    override suspend fun setBypassMainland(enabled: Boolean) { notUsed() }
    override suspend fun setRoutePreset(value: RoutePreset) {
        preset.value = value
        wrote()
    }
    override suspend fun setBlockAds(enabled: Boolean) {
        ads.value = enabled
        wrote()
    }
    override suspend fun setRulePackSubscribed(id: String, subscribed: Boolean) {
        rulePacks.value = if (subscribed) rulePacks.value + id else rulePacks.value - id
        wrote()
    }
    override suspend fun setLocalApiEnabled(enabled: Boolean) { notUsed() }
    override suspend fun setDnsMetricsEnabled(enabled: Boolean) {}
    override suspend fun setOnboardingDone(done: Boolean) { notUsed() }
    override suspend fun setAutoReconnect(enabled: Boolean) { notUsed() }
    override suspend fun setAutoSwitchOnDrop(enabled: Boolean) { notUsed() }
    override suspend fun setKillSwitchMode(mode: KillSwitchMode) { notUsed() }
    override suspend fun setPerAppRoutes(routes: Map<String, AppRoute>) {
        perAppRoutesFlow.value = routes
    }
    override suspend fun recordLeakCheck(record: LeakCheckRecord) { notUsed() }
    override suspend fun recordSubscriptionSuccess(atEpochMs: Long) { notUsed() }
    override suspend fun recordSubscriptionFailure(atEpochMs: Long, reason: String) { notUsed() }
}

private class FakeProfiles(private val all: List<Profile>) : ProfileRepository {

    override suspend fun getById(id: Long): Profile? = all.firstOrNull { it.id == id }

    override fun observeAll(): Flow<List<Profile>> = flowOf(all)

    override fun observeByGroup(groupId: Long): Flow<List<Profile>> = notUsed()
    override suspend fun upsert(profile: Profile): Long = notUsed()
    override suspend fun deleteById(id: Long) { notUsed() }
    override suspend fun deleteByGroup(groupId: Long) { notUsed() }
    override suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>) { notUsed() }
}

/** A group store with no writing: the dashboard only reads. */
private class FakeGroups(private val all: List<ProxyGroup>) : GroupRepository {
    override fun observeAll(): Flow<List<ProxyGroup>> = flowOf(all)
    override suspend fun getById(id: Long): ProxyGroup? = all.firstOrNull { it.id == id }
    override suspend fun findBySubscriptionUrl(url: String): ProxyGroup? = notUsed()
    override suspend fun findByRegion(region: NodeRegion): ProxyGroup? = notUsed()
    override suspend fun upsert(group: ProxyGroup): Long = notUsed()
    override suspend fun deleteById(id: Long) = notUsed()
    override suspend fun deleteWithProfiles(id: Long, deleteProfiles: Boolean) = notUsed()
}

/** No rules configured — the config just has an empty `rules` array. */
private class FakeRules : RuleRepository {
    override fun observeAllOrdered(): Flow<List<Rule>> = flowOf(emptyList())
    override suspend fun getById(id: Long): Rule? = notUsed()
    override suspend fun upsert(rule: Rule): Long = notUsed()
    override suspend fun deleteById(id: Long) { notUsed() }
}

/** Records what the kernel was actually asked to run. */
private class RecordingKernel : KernelBridge {

    var starts = 0
        private set
    var blocks = 0
        private set
    var lastConfig: String? = null
        private set

    /** When set, the next [start] dies immediately — a flapping node. */
    var crashOnStart = false

    /** When set, [start] refuses without emitting a crash, like a bad config. */
    var refuseOnStart = false

    /** When set, [block] fails: there is no tunnel to turn into a block. */
    var blockFails = false

    private val _state = MutableStateFlow<KernelState>(KernelState.Stopped)
    override val state: StateFlow<KernelState> = _state
    private val _blocking = MutableStateFlow(false)
    override val blocking: StateFlow<Boolean> = _blocking
    override val logs: SharedFlow<KernelLogLine> = MutableSharedFlow()
    override val connections: SharedFlow<KernelConnection> = MutableSharedFlow()

    /** The kernel died under us, the way the real bridge reports a crash. */
    fun crash() {
        _state.value = KernelState.Error(KernelError.Crashed("kernel stopped unexpectedly"))
    }

    /**
     * When set, [start] waits on it. Lets a test look at the state *during* a
     * connect, which is otherwise invisible because everything runs unconfined.
     */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun start(config: String): Result<Unit> {
        starts++
        lastConfig = config
        gate?.await()
        if (crashOnStart) {
            _state.value = KernelState.Error(KernelError.Crashed("kernel stopped unexpectedly"))
            return Result.failure(IllegalStateException("kernel stopped unexpectedly"))
        }
        if (refuseOnStart) {
            return Result.failure(IllegalStateException("kernel refused"))
        }
        _blocking.value = false
        _state.value = KernelState.Running
        return Result.success(Unit)
    }

    override suspend fun block(): Result<Unit> {
        blocks++
        if (blockFails) {
            _blocking.value = false
            return Result.failure(IllegalStateException("no tunnel to keep"))
        }
        _blocking.value = true
        _state.value = KernelState.Running
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        _blocking.value = false
        _state.value = KernelState.Stopped
        return Result.success(Unit)
    }

    /** The blackhole: kernel gone, tunnel kept, packets still dropped. */
    fun stopKeepingTunnel() {
        _blocking.value = true
        _state.value = KernelState.Stopped
    }

    override suspend fun queryStats(): TrafficStats = TrafficStats()
    override suspend fun version(): String = "test"
}

/** Health history, keyed by node id. */
private class FakeNodeHealth(
    private val byNode: Map<Long, List<LatencySample>> = emptyMap(),
) : NodeHealthRepository {
    override fun observeAll(): Flow<Map<Long, List<LatencySample>>> = flowOf(byNode)
    override suspend fun record(
        profileId: Long,
        latencyMs: Long,
        atMs: Long,
        weightTenths: Int,
    ) = notUsed()
    override suspend fun forget(profileId: Long) = notUsed()
}

private fun notUsed(): Nothing = throw NotImplementedError("not used by this test")

/** No network in a unit test; the rule then carries only the always-direct list. */
private object NoLocalSubnets : com.justnpcx.starveil.core.data.network.LocalSubnets {
    override fun current(): List<String> = emptyList()
}
