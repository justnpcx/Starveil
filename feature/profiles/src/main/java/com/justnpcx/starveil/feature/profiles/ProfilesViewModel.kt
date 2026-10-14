package com.justnpcx.starveil.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.subscription.ImportOutcome
import com.justnpcx.starveil.core.subscription.LinkImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val selectedId: Long = 0L,
    val loading: Boolean = true,
    val importing: Boolean = false,
    /** Result of the last import; already user-facing. */
    val message: String? = null,
)

/**
 * The node list plus the import entry point. Import goes through
 * [LinkImporter], so this module never touches the kernel.
 */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
    private val importer: LinkImporter,
) : ViewModel() {

    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfilesUiState> = combine(
        profiles.observeAll(),
        preferences.selectedProfileId,
        importing,
        message,
    ) { list, selectedId, isImporting, msg ->
        ProfilesUiState(
            profiles = list,
            selectedId = selectedId,
            loading = false,
            importing = isImporting,
            message = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfilesUiState(),
    )

    fun select(id: Long) {
        viewModelScope.launch { preferences.setSelectedProfileId(id) }
    }

    /** Paste a share link, or a subscription URL. */
    fun import(text: String) {
        viewModelScope.launch {
            importing.value = true
            message.value = when (val outcome = importer.import(text)) {
                is ImportOutcome.AddedNode -> "Added ${outcome.name}"
                is ImportOutcome.AddedSubscription -> "Imported ${outcome.nodeCount} nodes"
                is ImportOutcome.Failed -> "Import failed: ${outcome.reason}"
            }
            importing.value = false
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    /** Surfaces a message produced outside this feature (e.g. the latency tester). */
    fun postMessage(text: String) {
        message.value = text
    }
}
