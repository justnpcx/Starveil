package com.justnpcx.starveil.feature.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RuleMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RulesUiState(
    val rules: List<Rule> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && rules.isEmpty()
}

/**
 * Routing rules, in the order they are evaluated. The list is stored (never
 * held here), so the kernel and this screen always agree on what is
 * configured.
 */
@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: RuleRepository,
) : ViewModel() {

    val uiState: StateFlow<RulesUiState> = repository.observeAllOrdered()
        .map { RulesUiState(rules = it, loading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RulesUiState(),
        )

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
