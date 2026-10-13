package com.justnpcx.starveil.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val selectedId: Long = 0L,
    val loading: Boolean = true,
)

/**
 * The node list. Selection is written to preferences so the Dashboard (and the
 * config builder) can pick it up — that is what turns "connect" into a real
 * outbound instead of the skeleton config.
 */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfilesUiState> =
        combine(profiles.observeAll(), preferences.selectedProfileId) { list, selectedId ->
            ProfilesUiState(profiles = list, selectedId = selectedId, loading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfilesUiState(),
        )

    fun select(id: Long) {
        viewModelScope.launch { preferences.setSelectedProfileId(id) }
    }
}
