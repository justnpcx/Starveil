package com.justnpcx.starveil.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.StagedProbePort
import com.justnpcx.starveil.core.model.StagedProbeReport
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the staged test is doing, or what it found. */
sealed interface StagedProbeUiState {
    data object Idle : StagedProbeUiState

    data class Running(val profileName: String) : StagedProbeUiState

    data class Done(val profileName: String, val report: StagedProbeReport) : StagedProbeUiState

    data class Failed(val profileName: String, val message: String) : StagedProbeUiState
}

/**
 * The per-node diagnostic behind the long-press menu's "staged test".
 *
 * It reads the profile fresh from the repository rather than taking the row's
 * copy: a node edited between the long-press and the tap would otherwise be
 * tested with stale settings.
 */
@HiltViewModel
class StagedProbeViewModel @Inject constructor(
    private val probe: StagedProbePort,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<StagedProbeUiState>(StagedProbeUiState.Idle)
    val state: StateFlow<StagedProbeUiState> = _state.asStateFlow()

    fun run(profile: Profile) {
        if (_state.value is StagedProbeUiState.Running) return
        _state.value = StagedProbeUiState.Running(profile.name)
        viewModelScope.launch {
            _state.value = try {
                val fresh = profiles.getById(profile.id) ?: profile
                StagedProbeUiState.Done(fresh.name, probe.probe(fresh))
            } catch (e: Exception) {
                StagedProbeUiState.Failed(profile.name, e::class.java.simpleName)
            }
        }
    }

    fun dismiss() {
        _state.value = StagedProbeUiState.Idle
    }
}
