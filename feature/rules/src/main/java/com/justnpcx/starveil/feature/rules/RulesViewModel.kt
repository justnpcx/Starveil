package com.justnpcx.starveil.feature.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.data.ruleset.MainlandRuleSetStore
import com.justnpcx.starveil.core.data.ruleset.RulePackFailure
import com.justnpcx.starveil.core.data.ruleset.RulePackStore
import com.justnpcx.starveil.core.data.ruleset.RulePacks
import com.justnpcx.starveil.core.data.ruleset.RuleSetFailure
import com.justnpcx.starveil.core.kernel.FlowLog
import com.justnpcx.starveil.core.model.FlowRecord
import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RuleMatcher
import com.justnpcx.starveil.core.model.RulePack
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

    /** True until the user has made a routing choice; drives the beginner card. */
    val beginnerMode: Boolean = false,
    /** Whether advertising domains are rejected, on top of any preset. */
    val blockAds: Boolean = false,
    /**
     * A rule set that failed verification, if any.
     *
     * Shown because the preset is then quietly not in force: without this the
     * page would claim a policy the kernel was never given.
     */
    val ruleSetFailure: RuleSetFailure? = null,
    /** The curated packs this build offers, in display order. */
    val availablePacks: List<RulePack> = RulePacks.ALL,
    /** Packs the user subscribed to, by id. */
    val subscribedPacks: Set<String> = emptySet(),
    /** A subscribed pack whose file could not be verified; it is not in force. */
    val rulePackFailure: RulePackFailure? = null,
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
            add(PresetRule.LAN_DIRECT)
            if (preset == RoutePreset.BYPASS_MAINLAND) {
                add(PresetRule.DOMESTIC_NAMES)
                add(PresetRule.DOMESTIC_ADDRESSES)
            }
        }

    /**
     * What happens to traffic no rule matched.
     *
     * It is a property of the preset, not a row the user can edit — but it is
     * the last thing evaluated, so the list has to end with it or the page
     * reads as if unmatched traffic vanished.
     */
    fun fallbackAction(): RuleAction =
        if (preset == RoutePreset.GLOBAL_DIRECT) RuleAction.DIRECT else RuleAction.PROXY

    val enabledUserRules: Int get() = rules.count { it.enabled }

    /** What the page claims is in force, across the preset, packs and rules. */
    val effectiveRuleCount: Int get() = presetRules.size + subscribedPacks.size + enabledUserRules
}

/** One rule the preset emits, for the transparency list. */
enum class PresetRule {
    ADS,

    /**
     * Private and multicast addresses → direct.
     *
     * Always in force, in every preset: it is infrastructure, not a preference.
     * Listed anyway, because "why is my NAS not going through the tunnel" is a
     * question the page should answer without a debugger.
     */
    LAN_DIRECT,

    DOMESTIC_NAMES,
    DOMESTIC_ADDRESSES,
}

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
    private val ruleSets: MainlandRuleSetStore,
    private val packStore: RulePackStore,
) : ViewModel() {

    /** The market's two signals, paired so the outer combine stays at five. */
    private val packs = combine(
        preferences.subscribedRulePacks,
        packStore.failure,
        preferences.beginnerMode,
    ) { subscribed, failure, beginner -> Triple(subscribed, failure, beginner) }

    val uiState: StateFlow<RulesUiState> =
        combine(
            repository.observeAllOrdered(),
            flowLog.records,
            preferences.routePreset,
            preferences.blockAds,
            ruleSets.failure,
        ) { rules, flow, preset, ads, failure ->
            RulesUiState(
                rules = rules,
                loading = false,
                recentFlow = flow,
                preset = preset,
                blockAds = ads,
                ruleSetFailure = failure,
            )
        }.combine(packs) { state, (subscribed, packFailure, beginner) ->
            state.copy(
                subscribedPacks = subscribed,
                rulePackFailure = packFailure,
                beginnerMode = beginner,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RulesUiState(),
        )

    fun setPreset(preset: RoutePreset) {
        viewModelScope.launch {
            // Stamped as manual: a scene must not undo this within the grace
            // window. Written before the preset so a scene evaluating at the
            // same instant sees the stamp.
            preferences.recordManualPresetChange(System.currentTimeMillis())
            preferences.setRoutePreset(preset)
        }
    }

    /** Keeps the beginner defaults and stops calling them defaults. */
    fun exitBeginnerMode() {
        viewModelScope.launch { preferences.exitBeginnerMode() }
    }

    fun setBlockAds(enabled: Boolean) {
        viewModelScope.launch { preferences.setBlockAds(enabled) }
    }

    /**
     * Subscribe to or leave a pack.
     *
     * The preference is the whole change: the dashboard watches it and rebuilds
     * the running config, so a subscription takes effect the same way a preset
     * change does — no separate "apply" step, and no path where the page says a
     * pack is on while the tunnel was never told.
     */
    fun setPackSubscribed(id: String, subscribed: Boolean) {
        viewModelScope.launch { preferences.setRulePackSubscribed(id, subscribed) }
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
        val index = uiState.value.rules.indexOfFirst { it.id == rule.id }
        if (index < 0) return
        moveTo(rule, if (up) index - 1 else index + 1)
    }

    /**
     * Put [rule] at the top or the bottom in one action.
     *
     * Twenty rules and a rule at the bottom is nineteen taps with the arrows;
     * the arrows stay because they are what an eye looks for, and these two are
     * what a long list actually needs. Same renumber as [move]: the stored
     * order values are rewritten from the new positions, so rules that shared
     * an order cannot end up in an ambiguous state.
     */
    fun moveToTop(rule: Rule) = moveTo(rule, 0)

    fun moveToBottom(rule: Rule) = moveTo(rule, uiState.value.rules.lastIndex)

    private fun moveTo(rule: Rule, target: Int) {
        viewModelScope.launch {
            val ordered = uiState.value.rules.toMutableList()
            val index = ordered.indexOfFirst { it.id == rule.id }
            if (index < 0 || target !in ordered.indices || index == target) return@launch

            val moved = ordered.removeAt(index)
            ordered.add(target, moved)

            ordered.forEachIndexed { position, item ->
                if (item.order != position) repository.upsert(item.copy(order = position))
            }
        }
    }
}
