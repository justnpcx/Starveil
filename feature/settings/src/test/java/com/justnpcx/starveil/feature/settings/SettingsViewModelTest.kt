package com.justnpcx.starveil.feature.settings

import com.justnpcx.starveil.core.data.backup.BackupFileStore
import com.justnpcx.starveil.core.data.backup.BackupManager
import com.justnpcx.starveil.core.data.crash.CrashReportingSwitch
import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.kernel.KernelLogLine
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.model.AppRoute
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.LeakCheckRecord
import com.justnpcx.starveil.core.model.NodeRegion
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.model.Rule
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * The kernel version reaches the screen through a second `combine` (the
 * preferences are combined first, then the version is folded in), which is
 * exactly the shape where a value can quietly never arrive — so it is pinned
 * rather than assumed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `turning crash reports off writes through immediately`() = runBlocking {
        // The switch is the only thing standing between a crash and the
        // network, so it must reach the store the sender reads — not just the
        // ui state.
        val crash = FakeCrashReporting(enabled = true)
        val viewModel = SettingsViewModel(
            FakePreferences(),
            FakeKernel(""),
            emptyBackup(),
            NoFiles(),
            crash,
        )
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.setCrashReports(false)
        yield()

        assertFalse(crash.isEnabled())
        assertFalse(viewModel.uiState.value.crashReports)
        job.cancel()
    }

    @Test
    fun `the kernel's version reaches the ui state`() = runBlocking {
        val viewModel = SettingsViewModel(FakePreferences(), FakeKernel("1.14.3-647c447"), emptyBackup(), NoFiles(), FakeCrashReporting())
        val job = launch { viewModel.uiState.collect {} }

        // The read happens in the view model's init, and the fold crosses a
        // combine, so it lands a dispatch turn or two after construction.
        suspend fun awaitVersion(): String {
            repeat(20) {
                val current = viewModel.uiState.value.kernelVersion
                if (current.isNotEmpty()) return current
                yield()
            }
            return viewModel.uiState.value.kernelVersion
        }

        assertEquals("1.14.3-647c447", awaitVersion())
        job.cancel()
    }

    @Test
    fun `an empty kernel version leaves the rest of the state alone`() = runBlocking {
        val viewModel = SettingsViewModel(FakePreferences(), FakeKernel(""), emptyBackup(), NoFiles(), FakeCrashReporting())
        val job = launch { viewModel.uiState.collect {} }
        yield()

        assertEquals("", viewModel.uiState.value.kernelVersion)
        // The preferences still made it through.
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
        job.cancel()
    }
}

private class FakeKernel(private val reportedVersion: String) : KernelBridge {
    override val state: StateFlow<KernelState> = MutableStateFlow(KernelState.Stopped)
    override val logs: SharedFlow<KernelLogLine> = MutableSharedFlow()
    override val connections: SharedFlow<KernelConnection> = MutableSharedFlow()

    override suspend fun start(config: String): Result<Unit> = Result.success(Unit)
    override suspend fun stop(): Result<Unit> = Result.success(Unit)
    override suspend fun stopKeepingTunnel(): Result<Unit> = Result.success(Unit)
    override suspend fun queryStats(): TrafficStats = TrafficStats()
    override suspend fun version(): String = reportedVersion
}

/** Only the members the settings state actually reads are real. */
private class FakePreferences : PreferencesRepository {

    override val themeMode: Flow<ThemeMode> = flowOf(ThemeMode.DARK)
    override val dynamicColor: Flow<Boolean> = flowOf(false)
    override val autoReconnect: Flow<Boolean> = flowOf(true)
    override val autoSwitchOnDrop: Flow<Boolean> = flowOf(true)
    override val bypassMainland: Flow<Boolean> = flowOf(false)
    override val killSwitchMode: Flow<KillSwitchMode> = flowOf(KillSwitchMode.OFF)
    override val lastLeakCheck: Flow<LeakCheckRecord> = flowOf(LeakCheckRecord())

    override suspend fun setThemeMode(mode: ThemeMode) = notUsed()
    override suspend fun setDynamicColor(enabled: Boolean) = notUsed()
    override suspend fun setBypassMainland(enabled: Boolean) = notUsed()
    override suspend fun setAutoReconnect(enabled: Boolean) = notUsed()
    override suspend fun setAutoSwitchOnDrop(enabled: Boolean) = notUsed()
    override suspend fun setKillSwitchMode(mode: KillSwitchMode) = notUsed()
    override suspend fun recordLeakCheck(record: LeakCheckRecord) = notUsed()

    override val selectedProfileId: Flow<Long> get() = notUsed()
    override val selectedGroupId: Flow<Long> get() = notUsed()
    override val connectionTarget: Flow<ConnectionTarget> get() = notUsed()
    override val subscriptionLastSuccessAt: Flow<Long> get() = notUsed()
    override val subscriptionLastFailureAt: Flow<Long> get() = notUsed()
    override val subscriptionLastError: Flow<String?> get() = notUsed()
    // Real rather than notUsed(): the ui state counts it on every emission.
    override val perAppRoutes: Flow<Map<String, AppRoute>> = flowOf(emptyMap())
    override suspend fun setSelectedProfileId(id: Long) = notUsed()
    override suspend fun setSelectedGroupId(id: Long) = notUsed()
    override suspend fun recordSubscriptionSuccess(atEpochMs: Long) = notUsed()
    override suspend fun recordSubscriptionFailure(atEpochMs: Long, reason: String) = notUsed()
    override suspend fun setPerAppRoutes(routes: Map<String, AppRoute>) = notUsed()
}

private fun notUsed(): Nothing = throw NotImplementedError("not used by this test")

/**
 * A [BackupManager] over empty stores. This test is about the preference and
 * kernel state; backup is constructed only because the view model needs one.
 */
private fun emptyBackup() = BackupManager(NoProfiles(), NoGroups(), NoRules())

private class NoFiles : BackupFileStore {
    override suspend fun write(uri: Uri, bytes: ByteArray) = notUsed()
    override suspend fun read(uri: Uri): ByteArray = notUsed()
}

private class NoProfiles : ProfileRepository {
    override fun observeAll(): Flow<List<Profile>> = flowOf(emptyList())
    override fun observeByGroup(groupId: Long): Flow<List<Profile>> = flowOf(emptyList())
    override suspend fun getById(id: Long): Profile? = notUsed()
    override suspend fun upsert(profile: Profile): Long = notUsed()
    override suspend fun deleteById(id: Long) = notUsed()
    override suspend fun deleteByGroup(groupId: Long) = notUsed()
    override suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>) = notUsed()
}

private class NoGroups : GroupRepository {
    override fun observeAll(): Flow<List<ProxyGroup>> = flowOf(emptyList())
    override suspend fun getById(id: Long): ProxyGroup? = notUsed()
    override suspend fun findBySubscriptionUrl(url: String): ProxyGroup? = notUsed()
    override suspend fun findByRegion(region: NodeRegion): ProxyGroup? = notUsed()
    override suspend fun upsert(group: ProxyGroup): Long = notUsed()
    override suspend fun deleteById(id: Long) = notUsed()
}

private class NoRules : RuleRepository {
    override fun observeAllOrdered(): Flow<List<Rule>> = flowOf(emptyList())
    override suspend fun getById(id: Long): Rule? = notUsed()
    override suspend fun upsert(rule: Rule): Long = notUsed()
    override suspend fun deleteById(id: Long) = notUsed()
}

/**
 * In memory, and default on — the same default the real preference has, so a
 * test that cares about the switch has to say so.
 */
private class FakeCrashReporting(private var enabled: Boolean = true) : CrashReportingSwitch {
    override fun isEnabled(): Boolean = enabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
