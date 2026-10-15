package com.justnpcx.starveil.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.subscription.ImportOutcome
import com.justnpcx.starveil.core.subscription.LinkImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfilesUiState(
    /** The nodes to show — already narrowed by [groupFilter]. */
    val profiles: List<Profile> = emptyList(),
    /** Every group, for the filter chips. */
    val groups: List<ProxyGroup> = emptyList(),
    /** null = show every group. */
    val groupFilter: Long? = null,
    /** Whether any node exists at all, filtered or not. */
    val hasProfiles: Boolean = false,
    /** Nodes exist, but the current filter hides them all. */
    val hasNoMatches: Boolean = false,
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
    private val groups: GroupRepository,
    private val preferences: PreferencesRepository,
    private val importer: LinkImporter,
) : ViewModel() {

    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val groupFilter = MutableStateFlow<Long?>(null)

    /** Paired so the two transient signals count as one flow to [combine]. */
    private val transient = combine(importing, message) { isImporting, msg -> isImporting to msg }

    val uiState: StateFlow<ProfilesUiState> = combine(
        profiles.observeAll(),
        groups.observeAll(),
        preferences.selectedProfileId,
        transient,
        groupFilter,
    ) { list, groupList, selectedId, (isImporting, msg), filter ->
        // A node imported from a share link has no group, so it only ever
        // shows under "All".
        val visible = if (filter == null) list else list.filter { it.groupId == filter }
        ProfilesUiState(
            profiles = visible,
            groups = groupList,
            groupFilter = filter,
            hasProfiles = list.isNotEmpty(),
            hasNoMatches = visible.isEmpty() && list.isNotEmpty(),
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

    /** [groupId] of null shows every group. */
    fun filterByGroup(groupId: Long?) {
        groupFilter.value = groupId
    }

    /**
     * Remove a node.
     *
     * When the deleted node is the selected one, clear the selection too:
     * leaving it set would have the dashboard name a node that no longer
     * exists, and the next connect would build a config from a missing
     * profile.
     */
    fun delete(id: Long) {
        viewModelScope.launch {
            profiles.deleteById(id)
            if (preferences.selectedProfileId.first() == id) {
                preferences.setSelectedProfileId(0L)
            }
        }
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
