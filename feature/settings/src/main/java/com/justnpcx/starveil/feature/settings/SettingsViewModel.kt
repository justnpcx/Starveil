package com.justnpcx.starveil.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.backup.BackupCryptoException
import com.justnpcx.starveil.core.data.backup.BackupFileStore
import com.justnpcx.starveil.core.data.backup.BackupManager
import com.justnpcx.starveil.core.data.crash.CrashReportingSwitch
import com.justnpcx.starveil.core.data.crypto.LocalApiTokenStore
import com.justnpcx.starveil.core.data.diagnostics.DiagnosticBundle
import com.justnpcx.starveil.core.data.diagnostics.DiagnosticInput
import com.justnpcx.starveil.core.data.geo.DnsMetrics
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.kernel.LogStream
import com.justnpcx.starveil.core.data.geo.DnsSnapshot
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.update.UpdateCheck
import com.justnpcx.starveil.core.data.update.UpdateChecker
import com.justnpcx.starveil.core.data.update.UpdateFailure
import com.justnpcx.starveil.core.data.update.UpdateInfo
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.ThemeMode
import com.justnpcx.starveil.core.model.LeakCheckRecord
import com.justnpcx.starveil.core.data.update.UpdateDownloadPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Settings screen renders. */
data class SettingsUiState(
    val killSwitchMode: KillSwitchMode = KillSwitchMode.OFF,
    val autoReconnect: Boolean = true,
    val autoSwitchOnDrop: Boolean = true,
    /** Light, dark, or follow the system. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lastLeakCheck: LeakCheckRecord = LeakCheckRecord(),
    /**
     * The core's own version string, e.g. `1.14.3-647c447`. Empty until the
     * first read lands.
     */
    val kernelVersion: String = "",
    /** How many apps are set to bypass the tunnel; shown on the entry row. */
    val perAppDirectCount: Int = 0,
    /**
     * Whether crash reports are sent. Only meaningful when the build carries a
     * DSN; the screen hides the row otherwise.
     */
    val crashReports: Boolean = true,
    /** Whether the local Clash-compatible API is served. Off unless asked for. */
    val localApiEnabled: Boolean = false,
    /** Whether DNS timings are sampled (developer options). */
    val dnsMetricsEnabled: Boolean = true,
    /** The current ring, as the developer options row shows it. */
    val dnsStats: DnsSnapshot = DnsSnapshot.EMPTY,
    /**
     * The bearer token a dashboard must send. Empty until the Keystore read
     * lands, and never logged.
     */
    val apiToken: String = "",
)

/**
 * What the last backup operation did.
 *
 * A sealed type rather than a string: the screen decides the wording, and a
 * failure reason from the crypto layer is never shown raw to someone who just
 * mistyped a passphrase.
 */
/** What the About section's update row is doing. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data object UpToDate : UpdateUiState
    /** The check itself failed — no network, no release, unreadable answer. */
    data class Failed(val reason: UpdateFailure) : UpdateUiState
    data object Downloading : UpdateUiState
    /** The download or its verification failed; [reason] names which check. */
    data class DownloadFailed(val reason: UpdateFailure) : UpdateUiState
    /** Downloaded and verified; the screen hands it to the installer. */
    data class Ready(val file: java.io.File) : UpdateUiState
}

sealed interface BackupMessage {
    data class Exported(val bytes: Int) : BackupMessage
    data class Restored(val profiles: Int, val groups: Int, val rules: Int) : BackupMessage
    data object WrongPassphrase : BackupMessage
    data class Failed(val reason: String) : BackupMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val kernel: KernelBridge,
    private val backup: BackupManager,
    private val files: BackupFileStore,
    private val crashReporting: CrashReportingSwitch,
    private val updateChecker: UpdateChecker,
    private val updateDownloader: UpdateDownloadPort,
    private val localApiToken: LocalApiTokenStore,
    private val metrics: DnsMetrics,
    private val profiles: ProfileRepository,
    private val groups: GroupRepository,
    private val rules: RuleRepository,
    private val logs: LogStream,
) : ViewModel() {

    private val _update = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val update: StateFlow<UpdateUiState> = _update.asStateFlow()

    /** Persist the appearance choice. The whole app re-themes above this. */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun checkForUpdates(currentVersion: String) {
        if (_update.value is UpdateUiState.Checking || _update.value is UpdateUiState.Downloading) return
        _update.value = UpdateUiState.Checking
        viewModelScope.launch {
            _update.value = when (val result = updateChecker.check(currentVersion)) {
                is UpdateCheck.Available -> UpdateUiState.Available(result.info)
                UpdateCheck.UpToDate -> UpdateUiState.UpToDate
                is UpdateCheck.Failed -> UpdateUiState.Failed(result.reason)
            }
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        _update.value = UpdateUiState.Downloading
        viewModelScope.launch {
            _update.value = when (val result = updateDownloader.download(info)) {
                is UpdateDownloadPort.Result.Ready -> UpdateUiState.Ready(result.file)
                is UpdateDownloadPort.Result.Failed -> UpdateUiState.DownloadFailed(result.reason)
            }
        }
    }

    fun clearUpdate() {
        _update.value = UpdateUiState.Idle
    }

    private val _backupMessage = MutableStateFlow<BackupMessage?>(null)
    val backupMessage: StateFlow<BackupMessage?> = _backupMessage.asStateFlow()

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    /**
     * Seal the current nodes, groups and rules into [uri].
     *
     * The passphrase is wiped as soon as it has been used, whether that worked
     * or not — it is a `CharArray` precisely so it can be.
     */
    fun export(uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            try {
                val blob = backup.export(passphrase)
                files.write(uri, blob)
                _backupMessage.value = BackupMessage.Exported(blob.size)
            } catch (e: Exception) {
                _backupMessage.value = BackupMessage.Failed(e.readable())
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    /**
     * Restore from [uri].
     *
     * A wrong passphrase and a corrupt file are the same failure to the crypto
     * layer — the GCM tag does not check — so they get one message. Nothing is
     * written until decryption succeeds, so a failed restore leaves the
     * database exactly as it was.
     */
    fun restore(uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            try {
                val blob = files.read(uri)
                val result = backup.restore(blob, passphrase)
                _backupMessage.value = BackupMessage.Restored(
                    profiles = result.profiles,
                    groups = result.groups,
                    rules = result.rules,
                )
            } catch (e: BackupCryptoException) {
                _backupMessage.value = BackupMessage.WrongPassphrase
            } catch (e: Exception) {
                _backupMessage.value = BackupMessage.Failed(e.readable())
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    /** A failure message that never carries anything but the exception's kind. */
    private fun Exception.readable(): String = message?.takeIf { it.isNotBlank() }
        ?: this::class.java.simpleName

    private val kernelVersion = MutableStateFlow("")

    // Not a DataStore flow: see CrashReportingPreference for why this one is
    // synchronous, and why the screen mirrors it rather than observing it.
    private val crashReports = MutableStateFlow(crashReporting.isEnabled())

    /** Read from the Keystore once, off the main thread. */
    private val apiToken = MutableStateFlow("")

    /** Declared before the init block, which reads it. */
    private val dnsStatsFlow = MutableStateFlow(DnsSnapshot.EMPTY)

    init {
        // The ring is on unless the switch says otherwise; the stored value is
        // the source of truth, and the metric has no opinion of its own.
        viewModelScope.launch {
            metrics.enabled = preferences.dnsMetricsEnabled.first()
        }
        // Read once. It is a build constant, and Settings is the one place it
        // is worth showing — a bug report needs to name the core it ran.
        viewModelScope.launch { kernelVersion.value = kernel.version() }
        // Reading the token creates it if this is the first time, which is
        // exactly when the developer section is opened for the first time.
        viewModelScope.launch(Dispatchers.IO) { apiToken.value = localApiToken.token() }
    }

    private val preferencesState = combine(
        preferences.killSwitchMode,
        preferences.themeMode,
        // Paired so the typed three-argument combine stays available; the two
        // reconnect switches are read together and always shown together.
        combine(preferences.autoReconnect, preferences.autoSwitchOnDrop) { reconnect, switch ->
            reconnect to switch
        },
        preferences.lastLeakCheck,
    ) { mode, theme, reconnect, leak ->
        SettingsUiState(
            killSwitchMode = mode,
            themeMode = theme,
            autoReconnect = reconnect.first,
            autoSwitchOnDrop = reconnect.second,
            lastLeakCheck = leak,
        )
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            preferencesState,
            kernelVersion,
            // Counted rather than listed: the row says how many, the screen
            // says which.
            preferences.perAppRoutes.map { it.size },
            crashReports,
            combine(
                preferences.localApiEnabled,
                preferences.dnsMetricsEnabled,
            ) { api, dns -> api to dns },
        ) { state, version, perAppCount, reports, apiAndDns ->
            state.copy(
                kernelVersion = version,
                perAppDirectCount = perAppCount,
                crashReports = reports,
                localApiEnabled = apiAndDns.first,
                dnsMetricsEnabled = apiAndDns.second,
            )
        }.combine(apiToken) { state, token ->
            state.copy(apiToken = token)
        }.combine(dnsStatsFlow) { state, stats ->
            state.copy(dnsStats = stats)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    /**
     * Build the diagnostic bundle and hand the text back.
     *
     * Nothing is written anywhere until the user confirms in the preview — this
     * function returns a string and that is all it does.
     */
    fun buildDiagnosticBundle(appVersion: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val nodeList = profiles.observeAll().first()
            val groupList = groups.observeAll().first()
            val ruleList = rules.observeAllOrdered().first()
            val token = localApiToken.token()
            val leak = preferences.lastLeakCheck.first()
            // The ring is oldest-first; the bundle keeps the most recent
            // hundred, in the order they happened.
            val logLines = logs.lines.first()
                .takeLast(MAX_LOG_LINES)
                .map { line ->
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date(line.timestampMs))
                    time + " " + line.level.name.take(1) + " " + line.message
                }

            val input = DiagnosticInput(
                appVersion = appVersion,
                kernelVersion = kernelVersion.value,
                androidSdk = android.os.Build.VERSION.SDK_INT,
                dns = metrics.snapshot(),
                leakCheckedAtMs = leak.checkedAtEpochMs,
                leakLeaking = leak.leaking,
                nodeCount = nodeList.size,
                groupCount = groupList.size,
                ruleCount = ruleList.size,
                routePreset = preferences.routePreset.first().name.lowercase(),
                killSwitchMode = preferences.killSwitchMode.first().name.lowercase(),
                localApiEnabled = preferences.localApiEnabled.first(),
                perAppDirectCount = preferences.perAppRoutes.first().size,
                dnsMetricsEnabled = preferences.dnsMetricsEnabled.first(),
                logLines = logLines,
                // The exact values this device must never publish. Servers,
                // subscription URLs and the API token are the three that the
                // shapes alone cannot reliably catch.
                secrets = buildList {
                    nodeList.forEach { add(it.server) }
                    groupList.forEach { group -> group.subscriptionUrl?.let { add(it) } }
                    if (token.isNotBlank()) add(token)
                },
            )
            onReady(DiagnosticBundle.build(input))
        }
    }

    /** Read the ring into the row; called while the developer group is open. */
    fun refreshDnsStats() {
        dnsStatsFlow.value = metrics.snapshot()
    }

    fun clearDnsStats() {
        metrics.clear()
        dnsStatsFlow.value = DnsSnapshot.EMPTY
    }

    fun setDnsMetricsEnabled(enabled: Boolean) {
        metrics.enabled = enabled
        viewModelScope.launch { preferences.setDnsMetricsEnabled(enabled) }
    }

    fun setKillSwitchMode(mode: KillSwitchMode) {
        viewModelScope.launch { preferences.setKillSwitchMode(mode) }
    }

    /**
     * Turning this on is a config change like any other: the dashboard watches
     * the preference and rebuilds the tunnel, so the listener starts when the
     * next config does rather than needing a separate start.
     */
    fun setLocalApiEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setLocalApiEnabled(enabled) }
    }

    /** Revoke the current token; every dashboard must be reconfigured. */
    fun resetApiToken() {
        viewModelScope.launch(Dispatchers.IO) { apiToken.value = localApiToken.reset() }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoReconnect(enabled) }
    }

    fun setAutoSwitchOnDrop(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoSwitchOnDrop(enabled) }
    }

    private companion object {
        /** Enough to see the last failure, small enough to paste. */
        const val MAX_LOG_LINES = 100
    }

    fun setCrashReports(enabled: Boolean) {
        // Written synchronously so the next crash already sees the new value;
        // Sentry reads it from its own thread, not from this flow.
        crashReporting.setEnabled(enabled)
        crashReports.value = enabled
    }
}
