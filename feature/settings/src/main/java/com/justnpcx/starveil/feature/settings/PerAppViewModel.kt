package com.justnpcx.starveil.feature.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One entry in the app picker. */
data class InstalledApp(
    val label: String,
    val packageName: String,
)

data class PerAppUiState(
    val apps: List<InstalledApp> = emptyList(),
    /** Packages that bypass the tunnel. */
    val selected: Set<String> = emptySet(),
    val loading: Boolean = true,
    val query: String = "",
) {
    /**
     * The list after the search box.
     *
     * Filtering both the label and the package name because either is what
     * someone types: the label is what they see, the package name is what they
     * find when the label is a localisation they do not recognise.
     */
    val visible: List<InstalledApp>
        get() = if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
}

/**
 * The per-app exception list.
 *
 * The tunnel is the default, so this screen only ever says "these apps do not
 * use it". The list is applied when the config is built, at connect time —
 * changing it while connected does nothing until the next connect, which is
 * why the screen says so rather than leaving people to wonder.
 */
@HiltViewModel
class PerAppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PerAppUiState())
    val uiState: StateFlow<PerAppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { loadLaunchableApps() }
            _uiState.update { it.copy(apps = apps, loading = false) }
        }
        viewModelScope.launch {
            preferences.perAppDirectPackages.collect { selected ->
                _uiState.update { it.copy(selected = selected) }
            }
        }
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun toggle(packageName: String) {
        val next = _uiState.value.selected.toMutableSet()
        if (!next.add(packageName)) next.remove(packageName)
        // Optimistic: the row must react on tap, and the repository is the
        // same value a moment later.
        _uiState.update { it.copy(selected = next) }
        viewModelScope.launch { preferences.setPerAppDirectPackages(next) }
    }

    /**
     * Apps with a launcher entry.
     *
     * Launcher apps rather than every installed package: a user picking "the
     * bank app" is thinking of something they can tap, and the full package
     * list is mostly libraries and system components. Ourselves excluded —
     * the process is already routed around the tunnel, so listing it would
     * be a switch that does nothing.
     */
    private fun loadLaunchableApps(): List<InstalledApp> {
        val packageManager = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcher, 0)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                InstalledApp(
                    label = info.loadLabel(packageManager).toString(),
                    packageName = packageName,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
