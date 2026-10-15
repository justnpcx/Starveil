package com.justnpcx.starveil.feature.rules

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * Rules — the routing table, in the order it is evaluated.
 *
 * A rule matches a domain or an IP range and then decides where that traffic
 * goes. With no rules everything falls through to the proxy (the route's
 * `final`), which is the honest default for a proxy client.
 */
@Composable
fun RulesScreen(
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Rule?>(null) }
    var pendingDelete by remember { mutableStateOf<Rule?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.rules_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(
                    start = StarveilSpacing.lg,
                    top = StarveilSpacing.lg,
                    end = StarveilSpacing.lg,
                ),
            )

            if (state.isEmpty) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(StarveilSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
                ) {
                    items(state.rules, key = { it.id }) { rule ->
                        val index = state.rules.indexOfFirst { it.id == rule.id }
                        RuleRow(
                            rule = rule,
                            canMoveUp = index > 0,
                            canMoveDown = index in 0 until state.rules.lastIndex,
                            onToggle = { viewModel.setEnabled(rule, it) },
                            onEdit = { editing = rule },
                            onDelete = { pendingDelete = rule },
                            onMoveUp = { viewModel.move(rule, up = true) },
                            onMoveDown = { viewModel.move(rule, up = false) },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(StarveilSpacing.lg),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.rules_add))
        }
    }

    if (showAdd || editing != null) {
        RuleDialog(
            initial = editing,
            onDismiss = {
                showAdd = false
                editing = null
            },
            onSave = { kind, value, action ->
                val target = editing
                if (target == null) viewModel.add(kind, value, action)
                else viewModel.update(target, kind, value, action)
                showAdd = false
                editing = null
            },
        )
    }

    pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.rules_delete)) },
            text = { Text("${kindLabel(rule.matcher.kind)}: ${rule.matcher.value}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(rule)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.rules_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.rules_cancel))
                }
            },
        )
    }
}

@Composable
private fun RuleRow(
    rule: Rule,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(StarveilSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${kindLabel(rule.matcher.kind)}: ${rule.matcher.value}",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = actionLabel(rule.action),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.rules_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.rules_delete)) }
                Box(modifier = Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.rules_move_up),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.rules_move_down),
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleDialog(
    initial: Rule?,
    onDismiss: () -> Unit,
    onSave: (MatcherKind, String, RuleAction) -> Unit,
) {
    var kind by remember { mutableStateOf(initial?.matcher?.kind ?: MatcherKind.DOMAIN_SUFFIX) }
    var value by remember { mutableStateOf(initial?.matcher?.value.orEmpty()) }
    var action by remember { mutableStateOf(initial?.action ?: RuleAction.PROXY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.rules_add_title else R.string.rules_edit_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                Text(
                    text = stringResource(R.string.rules_match_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
                ) {
                    MatcherKind.entries.forEach { candidate ->
                        FilterChip(
                            selected = kind == candidate,
                            onClick = { kind = candidate },
                            label = { Text(kindLabel(candidate)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.rules_value_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.rules_action_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
                ) {
                    RuleAction.entries.forEach { candidate ->
                        FilterChip(
                            selected = action == candidate,
                            onClick = { action = candidate },
                            label = { Text(actionLabel(candidate)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(kind, value, action) },
                enabled = value.isNotBlank(),
            ) {
                Text(stringResource(R.string.rules_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.rules_cancel)) }
        },
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(StarveilSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        Text(text = stringResource(R.string.rules_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.rules_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun kindLabel(kind: MatcherKind): String = stringResource(
    when (kind) {
        MatcherKind.DOMAIN -> R.string.rules_kind_domain
        MatcherKind.DOMAIN_SUFFIX -> R.string.rules_kind_domain_suffix
        MatcherKind.DOMAIN_KEYWORD -> R.string.rules_kind_domain_keyword
        MatcherKind.IP_CIDR -> R.string.rules_kind_ip_cidr
    },
)

@Composable
private fun actionLabel(action: RuleAction): String = stringResource(
    when (action) {
        RuleAction.PROXY -> R.string.rules_action_proxy
        RuleAction.DIRECT -> R.string.rules_action_direct
        RuleAction.BLOCK -> R.string.rules_action_block
    },
)
