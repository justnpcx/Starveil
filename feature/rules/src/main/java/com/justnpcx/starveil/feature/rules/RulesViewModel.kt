package com.justnpcx.starveil.feature.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.kernel.FlowLog
import com.justnpcx.starveil.core.model.FlowRecord
import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RuleMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RulesUiState(
    val rules: List<Rule> = emptyList(),
    val loading: Boolean = true,
    /**
     * Recent routing decisions, newest first — what the kernel actually did,
     * as opposed to [rules], which is what it was told to do. The two are shown
     * together on purpose: they only disagree when something is wrong, and that
     * is the thing this screen exists to make visible.
     */
    val recentFlow: List<FlowRecord> = emptyList(),
    /**
     * Whether domestic traffic goes direct.
     *
     * A preference, not a statement that the preset is in force: the rule set
     * files may not be on disk, and the config is built without the preset when
     * they are not. This is what the user asked for; what they get is decided
     * at connect time.
     */
    val preset: RoutePreset = RoutePreset.GLOBAL_PROXY,
    /** Whether advertising domains are rejected, on top of any preset. */
    val blockAds: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && rules.isEmpty()

    /**
     * Rules the preset itself contributes, in the order they are evaluated.
     *
     * Shown rather than counted: "3 rules in force" answers how many, and the
     * next question is always which.
     */
    val presetRules: List<PresetRule>
        get() = buildList {
            if (blockAds) add(PresetRule.ADS)
            if (preset == RoutePreset.BYPASS_MAINLAND) {
                add(PresetRule.DOMESTIC_NAMES)
                add(PresetRule.DOMESTIC_ADDRESSES)
            }
        }

    val enabledUserRules: Int get() = rules.count { it.enabled }
}

/** One rule the preset emits, for the transparency list. */
enum class PresetRule { ADS, DOMESTIC_NAMES, DOMESTIC_ADDRESSES }

/**
 * Routing rules, in the order they are evaluated, plus the kernel's recent
 * decisions. The list is stored (never held here), so the kernel and this
 * screen always agree on what is configured.
 */
@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: RuleRepository,
    private val flowLog: FlowLog,
    private val preferences: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<RulesUiState> =
        combine(
            repository.observeAllOrdered(),
            flowLog.records,
            preferences.routePreset,
            preferences.blockAds,
        ) { rules, flow, preset, ads ->
            RulesUiState(
                rules = rules,
                loading = false,
                recentFlow = flow,
                preset = preset,
                blockAds = ads,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RulesUiState(),
        )

    fun setPreset(preset: RoutePreset) {
        viewModelScope.launch { preferences.setRoutePreset(preset) }
    }

    fun setBlockAds(enabled: Boolean) {
        viewModelScope.launch { preferences.setBlockAds(enabled) }
    }

    /** Appends: the first matching rule wins, so a new one belongs last. */
    fun add(kind: MatcherKind, value: String, action: RuleAction) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val nextOrder = (uiState.value.rules.maxOfOrNull { it.order } ?: -1) + 1
            repository.upsert(
                Rule(matcher = RuleMatcher(kind, trimmed), action = action, order = nextOrder),
            )
        }
    }

    fun update(rule: Rule, kind: MatcherKind, value: String, action: RuleAction) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.upsert(rule.copy(matcher = RuleMatcher(kind, trimmed), action = action))
        }
    }

    fun setEnabled(rule: Rule, enabled: Boolean) {
        viewModelScope.launch { repository.upsert(rule.copy(enabled = enabled)) }
    }

    fun delete(rule: Rule) {
        viewModelScope.launch { repository.deleteById(rule.id) }
    }

    /**
     * Swap [rule] with its neighbour and renumber the whole list. Renumbering
     * (rather than swapping the two stored values) is what makes this correct
     * for rules that were added before an order existed and therefore share a
     * value — swapping equal values would be a no-op.
     */
    fun move(rule: Rule, up: Boolean) {
        viewModelScope.launch {
            val ordered = uiState.value.rules.toMutableList()
            val index = ordered.indexOfFirst { it.id == rule.id }
            if (index < 0) return@launch
            val target = if (up) index - 1 else index + 1
            if (target !in ordered.indices) return@launch

            val moved = ordered[index]
            ordered[index] = ordered[target]
            ordered[target] = moved

            ordered.forEachIndexed { position, item ->
                if (item.order != position) repository.upsert(item.copy(order = position))
            }
        }
    }
}
