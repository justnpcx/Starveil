package com.justnpcx.starveil.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.NodeCandidate
import com.justnpcx.starveil.core.model.NodePicker
import com.justnpcx.starveil.core.model.NodeRegions
import com.justnpcx.starveil.core.model.nodeHealthOf
import com.justnpcx.starveil.core.subscription.ImportFailure
import com.justnpcx.starveil.core.subscription.SubscriptionFailure
import com.justnpcx.starveil.core.subscription.ImportOutcome
import com.justnpcx.starveil.feature.dashboard.ConnectionOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Three steps: what this is, bring a node in, connect. */
data class OnboardingUiState(
    val step: Int = 0,
    /** A clipboard string that looks importable, if there was one. */
    val detected: String? = null,
    val importing: Boolean = false,
    val importResult: ImportResult? = null,
    val connecting: Boolean = false,
    val connectResult: ConnectResult? = null,
)

/** What step two did, as data — the screen owns the wording. */
sealed interface ImportResult {
    data class Imported(val nodes: Int, val dropped: Int) : ImportResult
    data class Failed(val failure: ImportFailure) : ImportResult
}

/** What step three did. */
sealed interface ConnectResult {
    data class Connected(val name: String?) : ConnectResult
    data object NoNodes : ConnectResult
    data class Failed(val reason: String?) : ConnectResult
}

/**
 * The first-run flow.
 *
 * It exists so a new install is not five tabs and a blank dashboard: bring in
 * one node, connect it, and the app is doing something. Everything it does is
 * the same code the rest of the app uses — the subscription importer and the
 * M5 picker — so "onboarding" cannot become a second, subtly different path
 * into the tunnel.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val importer: NodeImporter,
    private val profiles: ProfileRepository,
    private val nodeHealth: NodeHealthRepository,
    private val preferences: PreferencesRepository,
    private val connectNow: ConnectNow,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /**
     * Offer the clipboard when it holds something usable.
     *
     * Deliberately the same permissive test the node list uses: anything with
     * a scheme, or an http(s) URL. A false positive costs one tap; a false
     * negative hides the one thing step two is for.
     */
    fun detectClipboard(text: String?) {
        val trimmed = text?.trim().orEmpty()
        _state.update {
            it.copy(detected = trimmed.takeIf { candidate -> looksImportable(candidate) })
        }
    }

    fun next() {
        _state.update { it.copy(step = (it.step + 1).coerceAtMost(LAST_STEP)) }
    }

    fun back() {
        _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    }

    /**
     * Import a link or a subscription URL.
     *
     * On success the flow moves itself to the connect step: the user's next
     * question is "now what", and the answer is the button on step three.
     */
    fun import(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.importing) return
        viewModelScope.launch {
            _state.update { it.copy(importing = true, importResult = null) }
            val result = try {
                when (val outcome = importer.import(trimmed)) {
                    is ImportOutcome.AddedNode -> ImportResult.Imported(nodes = 1, dropped = 0)
                    is ImportOutcome.AddedSubscription ->
                        ImportResult.Imported(outcome.nodeCount, outcome.dropped)
                    is ImportOutcome.Failed -> ImportResult.Failed(outcome.failure)
                }
            } catch (e: Exception) {
                ImportResult.Failed(ImportFailure.Subscription(SubscriptionFailure.Network))
            }
            _state.update {
                it.copy(
                    importing = false,
                    importResult = result,
                    step = if (result is ImportResult.Imported) CONNECT_STEP else it.step,
                )
            }
        }
    }

    /**
     * Pick the best node and connect, in one tap.
     *
     * The picker is [NodePicker] — the same one behind the dashboard's
     * auto-select — fed from the same health samples. The only difference is
     * that this path connects immediately instead of waiting for the dashboard
     * to observe the selection.
     */
    fun connectBest() {
        if (_state.value.connecting) return
        viewModelScope.launch {
            _state.update { it.copy(connecting = true, connectResult = null) }
            val pick = bestNode()
            val result = if (pick == null) {
                ConnectResult.NoNodes
            } else {
                val name = profiles.getById(pick)?.name
                preferences.setSelectedProfileId(pick)
                when (val outcome = connectNow.connect()) {
                    is ConnectionOutcome.Connected ->
                        ConnectResult.Connected(name ?: outcome.label)
                    is ConnectionOutcome.Refused -> ConnectResult.Failed(outcome.reason)
                    is ConnectionOutcome.Blocked -> ConnectResult.Failed(outcome.reason)
                    ConnectionOutcome.NoTarget -> ConnectResult.NoNodes
                    ConnectionOutcome.ConfigUnbuildable -> ConnectResult.Failed(null)
                    ConnectionOutcome.Disconnected -> ConnectResult.Failed(null)
                }
            }
            _state.update { it.copy(connecting = false, connectResult = result) }
        }
    }

    /**
     * Close the flow for good, whether it succeeded or was skipped.
     *
     * "Skip" is an answer: writing the same flag is what keeps the screen from
     * coming back, which is the promise it makes.
     */
    fun finish() {
        viewModelScope.launch { preferences.setOnboardingDone(true) }
    }

    private suspend fun bestNode(): Long? {
        val nodes = profiles.observeAll().first()
        if (nodes.isEmpty()) return null
        val samples = nodeHealth.observeAll().first()
        val selectedId = preferences.selectedProfileId.first()
        val preferredRegion = nodes.firstOrNull { it.id == selectedId }
            ?.let { NodeRegions.resolve(it.name, it.country) }
        return NodePicker.recommend(
            candidates = nodes.map { profile ->
                NodeCandidate(
                    profileId = profile.id,
                    region = NodeRegions.resolve(profile.name, profile.country),
                    health = samples[profile.id]?.let(::nodeHealthOf),
                )
            },
            preferredRegion = preferredRegion,
            nowMs = System.currentTimeMillis(),
        )?.profileId
    }

    private companion object {
        const val CONNECT_STEP = 2
        const val LAST_STEP = 2

        fun looksImportable(text: String): Boolean =
            text.contains("://") || text.startsWith("http", ignoreCase = true)
    }
}
