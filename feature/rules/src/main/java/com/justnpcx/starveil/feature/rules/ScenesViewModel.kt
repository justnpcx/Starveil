package com.justnpcx.starveil.feature.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.model.Scene
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScenesUiState(
    val scenes: List<Scene> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val groups: List<ProxyGroup> = emptyList(),
) {
    val isEmpty: Boolean get() = scenes.isEmpty()
}

/**
 * The scene list.
 *
 * Everything is stored, nothing is held: the list is small, it is edited rarely,
 * and keeping a copy here would only give it a second place to drift.
 */
@HiltViewModel
class ScenesViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    profiles: ProfileRepository,
    groups: GroupRepository,
) : ViewModel() {

    val uiState: StateFlow<ScenesUiState> = combine(
        preferences.scenes,
        profiles.observeAll(),
        groups.observeAll(),
    ) { scenes, nodes, groups ->
        ScenesUiState(scenes = scenes, profiles = nodes, groups = groups)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScenesUiState(),
    )

    /**
     * Adds a scene, or replaces the one with the same id.
     *
     * New scenes go last: order is priority, and appending is the least
     * surprising place for something the user just created.
     */
    fun save(scene: Scene) {
        viewModelScope.launch {
            val current = uiState.value.scenes
            val existing = current.indexOfFirst { it.id == scene.id }
            val updated = if (existing >= 0) {
                current.toMutableList().also { it[existing] = scene }
            } else {
                current + scene
            }
            preferences.setScenes(updated)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            preferences.setScenes(uiState.value.scenes.filterNot { it.id == id })
        }
    }

    /** Moves a scene up or down; the order is the priority. */
    fun move(id: Long, up: Boolean) {
        viewModelScope.launch {
            val current = uiState.value.scenes.toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) return@launch
            val target = if (up) index - 1 else index + 1
            if (target !in current.indices) return@launch
            val moved = current.removeAt(index)
            current.add(target, moved)
            preferences.setScenes(current)
        }
    }
}
