package com.justnpcx.starveil.feature.dashboard

import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelError
import com.justnpcx.starveil.core.kernel.KernelLogLine
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.LeakCheckRecord
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.VlessSettings
import com.justnpcx.starveil.core.ui.components.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(1, kernel.starts)
        // No node at all: nothing to reach the network with.
        assertFalse(kernel.lastConfig.orEmpty().contains("one.example"))
        assertTrue(kernel.lastConfig.orEmpty().contains("\"final\":\"block\""))
    }

    @Test
    fun `block on disconnect keeps the tunnel standing`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(FakePreferences(ONE, KillSwitchMode.BLOCK_ON_DISCONNECT), kernel)

        viewModel.onCoreClick() // connect
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionState)

        viewModel.onCoreClick() // disconnect
        // Tearing the tunnel down here is the leak this mode exists to stop.
        assertEquals(ConnectionState.BLOCKED, viewModel.uiState.value.connectionState)
        assertEquals(1, kernel.stopKeepingTunnelCalls)
    }

    @Test
    fun `with the kill switch off, disconnecting really disconnects`() = runBlocking {
        val kernel = RecordingKernel()
        val viewModel = viewModel(FakePreferences(ONE, KillSwitchMode.OFF), kernel)

        viewModel.onCoreClick()
        viewModel.onCoreClick()

        assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.connectionState)
        assertEquals(0, kernel.stopKeepingTunnelCalls)
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
        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("Pick a node or a group"))
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
        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("Pick a node or a group"))
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

    private fun viewModel(
        preferences: FakePreferences,
        kernel: RecordingKernel,
        groups: FakeGroups = FakeGroups(emptyList()),
    ) =
        DashboardViewModel(
            kernel = kernel,
            profiles = FakeProfiles(listOf(profile(ONE, "one"), profile(TWO, "two"))),
            preferences = preferences,
            groups = groups,
            rules = FakeRules(),
            configBuilder = ConfigBuilder(ProtocolRegistry.default()),
        )

    private fun profile(id: Long, name: String) = Profile(
        id = id,
        name = name,
        type = ProfileType.VLESS,
        server = "$name.example",
        port = 443,
        settings = VlessSettings(uuid = "11111111-2222-3333-4444-555555555555"),
    )

    private fun group(id: Long, mode: GroupMode, members: List<Long>) =
        ProxyGroup(id = id, name = "sub", mode = mode, profileIds = members)

    private companion object {
        const val ONE = 1L
        const val TWO = 2L
        const val SUB = 7L
    }
}

private class FakePreferences(
    initialId: Long,
    initialKillSwitch: KillSwitchMode = KillSwitchMode.OFF,
    autoReconnect: Boolean = true,
) : PreferencesRepository {

    private val killSwitch = MutableStateFlow(initialKillSwitch)
    private val autoReconnectFlow = MutableStateFlow(autoReconnect)

    private val target = MutableStateFlow<ConnectionTarget>(
        if (initialId != 0L) ConnectionTarget.Node(initialId) else ConnectionTarget.None,
    )

    override val selectedProfileId: Flow<Long> =
        target.map { (it as? ConnectionTarget.Node)?.id ?: 0L }
    override val selectedGroupId: Flow<Long> =
        target.map { (it as? ConnectionTarget.Group)?.id ?: 0L }
    override val connectionTarget: Flow<ConnectionTarget> = target

    override val killSwitchMode: Flow<KillSwitchMode> = killSwitch
    override val autoReconnect: Flow<Boolean> = autoReconnectFlow

    override suspend fun setSelectedProfileId(id: Long) {
        // Clearing the node must not disturb a group that is selected instead;
        // the real repository only touches its own key.
        target.value = when {
            id != 0L -> ConnectionTarget.Node(id)
            target.value is ConnectionTarget.Node -> ConnectionTarget.None
            else -> target.value
        }
    }

    override suspend fun setSelectedGroupId(id: Long) {
        target.value = when {
            id != 0L -> ConnectionTarget.Group(id)
            target.value is ConnectionTarget.Group -> ConnectionTarget.None
            else -> target.value
        }
    }

    // The rest of the preferences are not what this test is about.
    override val themeMode: Flow<ThemeMode> get() = notUsed()
    override val dynamicColor: Flow<Boolean> get() = notUsed()
    override val lastLeakCheck: Flow<LeakCheckRecord> get() = notUsed()
    override val subscriptionLastSuccessAt: Flow<Long> get() = notUsed()
    override val subscriptionLastFailureAt: Flow<Long> get() = notUsed()
    override val subscriptionLastError: Flow<String?> get() = notUsed()
    override suspend fun setThemeMode(mode: ThemeMode) { notUsed() }
    override suspend fun setDynamicColor(enabled: Boolean) { notUsed() }
    override suspend fun setAutoReconnect(enabled: Boolean) { notUsed() }
    override suspend fun setKillSwitchMode(mode: KillSwitchMode) { notUsed() }
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
    override suspend fun upsert(group: ProxyGroup): Long = notUsed()
    override suspend fun deleteById(id: Long) = notUsed()
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
    var stopKeepingTunnelCalls = 0
        private set
    var lastConfig: String? = null
        private set

    /** When set, the next [start] dies immediately — a flapping node. */
    var crashOnStart = false

    private val _state = MutableStateFlow<KernelState>(KernelState.Stopped)
    override val state: StateFlow<KernelState> = _state
    override val logs: SharedFlow<KernelLogLine> = MutableSharedFlow()

    /** The kernel died under us, the way the real bridge reports a crash. */
    fun crash() {
        _state.value = KernelState.Error(KernelError.Crashed("kernel stopped unexpectedly"))
    }

    override suspend fun start(config: String): Result<Unit> {
        starts++
        lastConfig = config
        if (crashOnStart) {
            _state.value = KernelState.Error(KernelError.Crashed("kernel stopped unexpectedly"))
            return Result.failure(IllegalStateException("kernel stopped unexpectedly"))
        }
        _state.value = KernelState.Running
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        _state.value = KernelState.Stopped
        return Result.success(Unit)
    }

    /** Records that the Kill Switch asked for the tunnel to stay standing. */
    override suspend fun stopKeepingTunnel(): Result<Unit> {
        stopKeepingTunnelCalls++
        _state.value = KernelState.Stopped
        return Result.success(Unit)
    }

    override suspend fun queryStats(): TrafficStats = TrafficStats()
}

private fun notUsed(): Nothing = throw NotImplementedError("not used by this test")
