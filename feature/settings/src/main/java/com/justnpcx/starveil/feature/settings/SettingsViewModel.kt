package com.justnpcx.starveil.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.backup.BackupCryptoException
import com.justnpcx.starveil.core.data.backup.BackupFileStore
import com.justnpcx.starveil.core.data.backup.BackupManager
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.LeakCheckRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
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
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val autoReconnect: Boolean = true,
    val lastLeakCheck: LeakCheckRecord = LeakCheckRecord(),
    /**
     * The core's own version string, e.g. `1.14.3-647c447`. Empty until the
     * first read lands.
     */
    val kernelVersion: String = "",
    /** How many apps are set to bypass the tunnel; shown on the entry row. */
    val perAppDirectCount: Int = 0,
)

/**
 * What the last backup operation did.
 *
 * A sealed type rather than a string: the screen decides the wording, and a
 * failure reason from the crypto layer is never shown raw to someone who just
 * mistyped a passphrase.
 */
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
) : ViewModel() {

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

    init {
        // Read once. It is a build constant, and Settings is the one place it
        // is worth showing — a bug report needs to name the core it ran.
        viewModelScope.launch { kernelVersion.value = kernel.version() }
    }

    private val preferencesState = combine(
        preferences.killSwitchMode,
        preferences.themeMode,
        preferences.dynamicColor,
        preferences.autoReconnect,
        preferences.lastLeakCheck,
    ) { mode, theme, dynamic, reconnect, leak ->
        SettingsUiState(
            killSwitchMode = mode,
            themeMode = theme,
            dynamicColor = dynamic,
            autoReconnect = reconnect,
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
        ) { state, version, perAppCount ->
            state.copy(kernelVersion = version, perAppDirectCount = perAppCount)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun setKillSwitchMode(mode: KillSwitchMode) {
        viewModelScope.launch { preferences.setKillSwitchMode(mode) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoReconnect(enabled) }
    }
}
