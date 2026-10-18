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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.model.FlowOutcome
import com.justnpcx.starveil.core.model.FlowRecord
import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.ui.components.AppearOnFirstShow
import com.justnpcx.starveil.core.ui.components.rememberAppearance
import com.justnpcx.starveil.core.ui.components.EmptyState
import com.justnpcx.starveil.core.ui.theme.StarveilColors
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
    /** Opens the per-app routing screen, which lives with the other settings. */
    onOpenPerApp: () -> Unit = {},
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Rule?>(null) }
    var pendingDelete by remember { mutableStateOf<Rule?>(null) }
    // §5.2. Hoisted out of the LazyColumn so a row scrolled away and back does
    // not replay its entrance.
    val appeared = rememberAppearance()

    // A rule set that failed its hash check leaves the preset out of the
    // config, which otherwise looks like the switch doing nothing. Said once,
    // when it happens, and left in the Logs screen afterwards.
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val ruleSetFailure = state.ruleSetFailure
    LaunchedEffect(ruleSetFailure) {
        if (ruleSetFailure != null) {
            snackbar.showSnackbar(context.getString(R.string.rules_ruleset_failed))
        }
    }

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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(StarveilSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
            ) {
                // The preset is a switch, not a rule: it is a whole routing
                // policy, and it belongs above both the decisions it produces
                // and the rules it sits in front of.
                item(key = PRESET_KEY) {
                    RoutePresetCard(
                        preset = state.preset,
                        blockAds = state.blockAds,
                        failed = state.ruleSetFailure != null,
                        presetRules = state.presetRules,
                        userRuleCount = state.enabledUserRules,
                        onSelect = viewModel::setPreset,
                        onToggleAds = viewModel::setBlockAds,
                        onOpenPerApp = onOpenPerApp,
                    )
                }

                // The kernel's actual decisions sit above the rules that were
                // meant to produce them: the list is what was configured, this
                // is what happened, and the difference is the whole point.
                item(key = FLOW_HEADER_KEY) { FlowHeader(state.recentFlow.isEmpty()) }
                state.recentFlow.take(SHOWN_FLOW_ROWS).forEach { record ->
                    item(key = "flow-${record.connectionId}") { FlowRow(record) }
                }

                if (state.isEmpty) {
                    item(key = "empty") {
                        EmptyState(
                            title = stringResource(R.string.rules_empty_title),
                            body = stringResource(
                                when (state.preset) {
                                    RoutePreset.BYPASS_MAINLAND -> R.string.rules_empty_body_bypass
                                    RoutePreset.GLOBAL_PROXY -> R.string.rules_empty_body_proxy
                                    RoutePreset.GLOBAL_DIRECT -> R.string.rules_empty_body_direct
                                },
                            ),
                        )
                    }
                } else {
                    itemsIndexed(state.rules, key = { _, rule -> rule.id }) { index, rule ->
                        // §5.2: rows settle in top-down. The index also replaces
                        // the per-row indexOfFirst lookup this used to do.
                        AppearOnFirstShow(key = rule.id, index = index, seen = appeared) {
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
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(StarveilSpacing.lg),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.rules_add))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
private fun FlowHeader(empty: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
        Text(
            text = stringResource(R.string.rules_flow_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = StarveilSpacing.sm),
        )
        if (empty) {
            Text(
                text = stringResource(R.string.rules_flow_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One real decision: where the request went, which rule sent it there, and how.
 *
 * Three lines rather than a drawn graph. A graph would have to be laid out from
 * something, and the only honest something is this record — so it is shown
 * directly instead of being redrawn as a diagram that could drift from it.
 */
@Composable
private fun FlowRow(record: FlowRecord) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(StarveilSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(record.outcome.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = record.outcome.colour(),
                )
            }
            // The rule in the kernel's own words. Deliberately not translated
            // into the list's vocabulary: it is quoted, not restated, and
            // restating it is how a viewer starts disagreeing with the kernel.
            if (record.rule.isNotBlank()) {
                Text(
                    text = record.rule,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FlowOutcome.labelRes(): Int = when (this) {
    // The same three words the rule list uses for its actions, so the two
    // halves of this screen cannot end up calling one outcome two things (K1).
    FlowOutcome.PROXY -> R.string.rules_action_proxy
    FlowOutcome.DIRECT -> R.string.rules_action_direct
    FlowOutcome.BLOCK -> R.string.rules_action_block
}

@Composable
private fun FlowOutcome.colour(): Color = when (this) {
    FlowOutcome.PROXY -> StarveilColors.GlowPrimary
    FlowOutcome.DIRECT -> StarveilColors.Connected
    FlowOutcome.BLOCK -> StarveilColors.Blocked
}

/** Keys and counts for the flow section. */
private const val FLOW_HEADER_KEY = "flow-header"
private const val PRESET_KEY = "mainland-preset"

/** Matches the node card on the Nodes screen; the two are the same kind of thing. */
private val CARD_CORNER = 16.dp

/**
 * How many decisions are shown above the rule list.
 *
 * The adapter keeps a longer ring; this is what is glanceable. Showing the
 * whole ring here would push the rules off the screen, and the rules are what
 * this screen is for.
 */
private const val SHOWN_FLOW_ROWS = 5

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

/**
 * The routing policy, as a choice rather than a switch.
 *
 * "Proxy everything" used to be the absence of a setting, which made the empty
 * state below say "everything goes through the proxy" even while the mainland
 * preset was on. Three named presets remove the contradiction: whatever is
 * selected is what the text can name.
 *
 * Ads stack on top rather than being a fourth preset, because blocking ads and
 * deciding where unblocked traffic goes are independent questions.
 */
@Composable
private fun RoutePresetCard(
    preset: RoutePreset,
    blockAds: Boolean,
    /** A rule set was rejected, so the preset below is not what the kernel gets. */
    failed: Boolean,
    presetRules: List<PresetRule>,
    userRuleCount: Int,
    onSelect: (RoutePreset) -> Unit,
    onToggleAds: (Boolean) -> Unit,
    onOpenPerApp: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(StarveilSpacing.lg)) {
            PresetRow(
                title = stringResource(R.string.rules_preset_title),
                description = stringResource(R.string.rules_preset_desc),
                selected = preset == RoutePreset.BYPASS_MAINLAND,
                onSelect = { onSelect(RoutePreset.BYPASS_MAINLAND) },
            )
            PresetRow(
                title = stringResource(R.string.rules_preset_proxy_title),
                description = stringResource(R.string.rules_preset_proxy_desc),
                selected = preset == RoutePreset.GLOBAL_PROXY,
                onSelect = { onSelect(RoutePreset.GLOBAL_PROXY) },
            )
            PresetRow(
                title = stringResource(R.string.rules_preset_direct_title),
                description = stringResource(R.string.rules_preset_direct_desc),
                selected = preset == RoutePreset.GLOBAL_DIRECT,
                onSelect = { onSelect(RoutePreset.GLOBAL_DIRECT) },
            )

            // Persistent, not only the Snackbar: the condition lasts until a
            // verified download succeeds, and the counts below would otherwise
            // go on claiming rules that are not in the config.
            if (failed) {
                Text(
                    text = stringResource(R.string.rules_ruleset_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = StarveilSpacing.sm),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = StarveilSpacing.sm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.rules_block_ads_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.rules_block_ads_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = blockAds, onCheckedChange = onToggleAds)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = StarveilSpacing.sm))

            // What the kernel will actually be told, not what the switches
            // look like: a preset that could not download its files degrades
            // at connect time, and this is where the user would notice.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.rules_effective_count,
                        presetRules.size + userRuleCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        if (expanded) R.string.rules_effective_collapse else R.string.rules_effective_expand,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = StarveilSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(StarveilSpacing.xs),
                ) {
                    presetRules.forEach { rule ->
                        Text(
                            text = stringResource(
                                when (rule) {
                                    PresetRule.ADS -> R.string.rules_effective_ads
                                    PresetRule.DOMESTIC_NAMES -> R.string.rules_effective_domestic_names
                                    PresetRule.DOMESTIC_ADDRESSES -> R.string.rules_effective_domestic_addresses
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (userRuleCount > 0) {
                        Text(
                            text = stringResource(R.string.rules_effective_user, userRuleCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = StarveilSpacing.sm))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPerApp)
                    .padding(vertical = StarveilSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.rules_per_app_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.rules_per_app_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PresetRow(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = StarveilSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = StarveilSpacing.sm)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
