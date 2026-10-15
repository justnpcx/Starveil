package com.justnpcx.starveil.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.LeakCheckRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Settings screen renders. */
data class SettingsUiState(
    val killSwitchMode: KillSwitchMode = KillSwitchMode.OFF,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val lastLeakCheck: LeakCheckRecord = LeakCheckRecord(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(
            preferences.killSwitchMode,
            preferences.themeMode,
            preferences.dynamicColor,
            preferences.lastLeakCheck,
        ) { mode, theme, dynamic, leak ->
            SettingsUiState(
                killSwitchMode = mode,
                themeMode = theme,
                dynamicColor = dynamic,
                lastLeakCheck = leak,
            )
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
}
