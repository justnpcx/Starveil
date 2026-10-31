package com.justnpcx.starveil.feature.rules

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.ui.components.StarveilSnackbarHost
import com.justnpcx.starveil.core.ui.components.StarveilTextField
import com.justnpcx.starveil.core.data.ruleset.RulePackFailure
import com.justnpcx.starveil.core.data.ruleset.RulePacks
import com.justnpcx.starveil.core.model.FlowOutcome
import com.justnpcx.starveil.core.model.FlowRecord
import com.justnpcx.starveil.core.model.FlowGroup
import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.groupFlow
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RulePack
import com.justnpcx.starveil.core.model.RulePackAction
import com.justnpcx.starveil.core.ui.components.AppearOnFirstShow
import com.justnpcx.starveil.core.ui.components.rememberAppearance
import com.justnpcx.starveil.core.ui.components.ChipSurface
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.components.StarveilChip
import com.justnpcx.starveil.core.ui.components.EmptyState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.justnpcx.starveil.core.ui.components.StarveilPlusDisc
import com.justnpcx.starveil.core.ui.components.StarveilButton
import com.justnpcx.starveil.core.ui.components.StarveilDivider
import com.justnpcx.starveil.core.ui.components.StarveilIconAction
import com.justnpcx.starveil.core.ui.components.StarveilPageHeader
import com.justnpcx.starveil.core.ui.components.StarveilSwitch
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.MonoFamily
import com.justnpcx.starveil.core.ui.theme.StarveilType
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilShapes
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

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
    onOpenScenes: () -> Unit = {},
    /** Opens the full-screen rule editor: 0 for a new rule, else the row's id. */
    onOpenRuleEditor: (Long) -> Unit = {},
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val highlight by viewModel.highlight.collectAsStateWithLifecycle()
    LaunchedEffect(highlight) {
        if (highlight != null) {
            delay(HIGHLIGHT_MS)
            viewModel.clearHighlight()
        }
    }
    var showMarket by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Rule?>(null) }
    var showAllFlow by remember { mutableStateOf(false) }
    var flowExpanded by remember { mutableStateOf(false) }
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
    val packFailure = state.rulePackFailure
    LaunchedEffect(packFailure) {
        if (packFailure != null) {
            snackbar.showSnackbar(
                context.getString(R.string.rules_market_failed, packTitle(context, packFailure.id)),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Room for the FAB: without it the last line of the empty state
                // sat under the button, which is how "全部走代理）" got covered.
                // The bottom clears the gesture bar and then some: a list that
                // ends flush against it loses its last row to the system.
                // The disc floats 12dp above the bar, so the last row stops
                // above it rather than behind it.
                contentPadding = PaddingValues(
                    start = StarveilSpacing.pageMargin,
                    top = StarveilSpacing.xs,
                    end = StarveilSpacing.pageMargin,
                    bottom = StarveilSpacing.plusClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.md),
            ) {
                // The preset is a switch, not a rule: it is a whole routing
                // policy, and it belongs above both the decisions it produces
                // and the rules it sits in front of.
                if (state.beginnerMode) {
                    item(key = BEGINNER_KEY) {
                        BeginnerCard(onCustomize = viewModel::exitBeginnerMode)
                    }
                }
                item(key = PRESET_KEY) {
                    RoutePresetCard(
                        preset = state.preset,
                        blockAds = state.blockAds,
                        failed = state.ruleSetFailure != null,
                        presetRules = state.presetRules,
                        userRuleCount = state.enabledUserRules,
                        subscribedPacks = state.subscribedPacks,
                        availablePacks = state.availablePacks,
                        onSelect = viewModel::setPreset,
                        onToggleAds = viewModel::setBlockAds,
                        onOpenMarket = { showMarket = true },
                        onOpenPerApp = onOpenPerApp,
                        onOpenScenes = onOpenScenes,
                    )
                }

                // The kernel's actual decisions sit above the rules that were
                // meant to produce them: the list is what was configured, this
                // is what happened, and the difference is the whole point.
                item(key = FLOW_HEADER_KEY) {
                    FlowSection(
                        records = state.recentFlow,
                        expanded = flowExpanded,
                        onToggleExpanded = { flowExpanded = !flowExpanded },
                        onOpenAll = { showAllFlow = true },
                    )
                }

                if (state.isEmpty) {
                    // In a card like every other block on the page: bare text
                    // between two glass cards read as a hole in the page.
                    item(key = "empty") {
                        StarveilCard {
                            EmptyState(
                                title = stringResource(R.string.rules_empty_title),
                                body = stringResource(R.string.rules_empty_body),
                            )
                        }
                    }
                } else {
                    // The user's own rules are one list, so they are one card.
                    // A card per rule was a stack of boxes with a box inside.
                    item(key = "rules") {
                        StarveilCard {
                            state.rules.forEachIndexed { index, rule ->
                                if (index > 0) {
                                    StarveilDivider(startInset = StarveilSpacing.lg)
                                }
                                RuleRow(
                                    rule = rule,
                                    position = index + 1,
                                    highlighted = rule.id == highlight,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < state.rules.lastIndex,
                                    onToggle = { viewModel.setEnabled(rule, it) },
                                    onEdit = { onOpenRuleEditor(rule.id) },
                                    onDelete = { pendingDelete = rule },
                                    onMoveUp = { viewModel.move(rule, up = true) },
                                    onMoveDown = { viewModel.move(rule, up = false) },
                                    onMoveTop = { viewModel.moveToTop(rule) },
                                    onMoveBottom = { viewModel.moveToBottom(rule) },
                                )
                            }
                            // The rule that is always there and never shown as a
                            // row: whatever falls through every rule above goes
                            // to the preset's own action. Without it the list
                            // reads as if unmatched traffic vanished.
                            StarveilDivider(startInset = StarveilSpacing.lg)
                            FallbackRow(
                                position = state.rules.size + 1,
                                action = state.fallbackAction(),
                            )
                        }
                    }
                }
            }
        }

        StarveilSnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // The same disc as the nodes and scenes pages. On this page it opens
        // the rule editor directly: a rule is four fields, and a sheet whose
        // only content was a second button for those fields was a step.
        StarveilPlusDisc(
            contentDescription = stringResource(R.string.rules_new),
            onClick = { onOpenRuleEditor(0L) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = StarveilSpacing.plusMarginEnd,
                    bottom = StarveilSpacing.plusMarginBottom,
                ),
        )
    }

    pendingDelete?.let { rule ->
        AlertDialog(
            containerColor = StarveilColors.card,
            shape = StarveilShapes.dialog,
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

    if (showMarket) {
        RuleMarketDialog(
            packs = state.availablePacks,
            subscribed = state.subscribedPacks,
            failure = state.rulePackFailure,
            onToggle = viewModel::setPackSubscribed,
            onDismiss = { showMarket = false },
        )
    }

    if (showAllFlow) {
        AllFlowDialog(
            records = state.recentFlow,
            onDismiss = { showAllFlow = false },
            // Reserved for Tap-to-Route (docs/16): the row is already the
            // target, so the gesture does not have to be invented later.
            onRecordClick = { },
        )
    }
}

/**
 * The market, as a sheet over the rules page rather than a screen of its own.
 *
 * A pack is one switch and one line of explanation; a destination with its own
 * back stack would be more navigation than content. When phase two adds
 * user-supplied sources it will need a screen — the entry point is already a
 * single callback, so that is where it changes.
 */
@Composable
private fun RuleMarketDialog(
    packs: List<RulePack>,
    subscribed: Set<String>,
    failure: RulePackFailure?,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
            containerColor = StarveilColors.card,
            shape = StarveilShapes.dialog,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_market_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.md),
            ) {
                Text(
                    text = stringResource(R.string.rules_market_desc),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                )
                if (failure != null) {
                    Text(
                        text = stringResource(
                            R.string.rules_market_failed,
                            packTitle(LocalContext.current, failure.id),
                        ),
                        style = StarveilType.label,
                        color = StarveilColors.errText,
                    )
                }
                packs.forEach { pack ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = packTitle(LocalContext.current, pack.id),
                                style = StarveilType.headline,
                            )
                            Text(
                                text = packDescription(LocalContext.current, pack.id),
                                style = StarveilType.label,
                                color = StarveilColors.ink2,
                            )
                        }
                        StarveilSwitch(
                            checked = pack.id in subscribed,
                            onCheckedChange = { onToggle(pack.id, it) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rules_market_close))
            }
        },
    )
}

/** The pack's name, or its id when a newer build subscribed to something this one does not know. */
private fun packTitle(context: android.content.Context, id: String): String {
    val res = when (id) {
        RulePacks.ADS_ENHANCED.id -> R.string.rules_pack_ads_enhanced
        RulePacks.ANTI_TRACKING.id -> R.string.rules_pack_anti_tracking
        RulePacks.STREAMING.id -> R.string.rules_pack_streaming
        RulePacks.DEVELOPER.id -> R.string.rules_pack_developer
        RulePacks.AI.id -> R.string.rules_pack_ai
        else -> return id
    }
    return context.getString(res)
}

private fun packDescription(context: android.content.Context, id: String): String {
    val res = when (id) {
        RulePacks.ADS_ENHANCED.id -> R.string.rules_pack_ads_enhanced_desc
        RulePacks.ANTI_TRACKING.id -> R.string.rules_pack_anti_tracking_desc
        RulePacks.STREAMING.id -> R.string.rules_pack_streaming_desc
        RulePacks.DEVELOPER.id -> R.string.rules_pack_developer_desc
        RulePacks.AI.id -> R.string.rules_pack_ai_desc
        else -> return ""
    }
    return context.getString(res)
}

@Composable
private fun RuleRow(
    rule: Rule,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveBottom: () -> Unit,
    /** True for the row just created from the add panel. */
    highlighted: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(StarveilShapes.card)
            .background(if (highlighted) StarveilColors.selBg else Color.Transparent),
    ) {
        Row(
            modifier = Modifier.padding(
                start = StarveilSpacing.lg,
                end = StarveilSpacing.md,
                top = StarveilSpacing.md,
                bottom = StarveilSpacing.xs,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "#$position",
                style = StarveilType.tech,
                color = StarveilColors.ink3,
                modifier = Modifier.width(ORDER_COLUMN_WIDTH),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kindLabel(rule.matcher.kind) + ": " + rule.matcher.value,
                    style = StarveilType.headline,
                    color = StarveilColors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = actionLabel(rule.action),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                )
            }
            StarveilSwitch(checked = rule.enabled, onCheckedChange = onToggle)
        }
        Row(
            modifier = Modifier.padding(
                start = StarveilSpacing.lg,
                end = StarveilSpacing.sm,
                bottom = StarveilSpacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.rules_edit),
                style = StarveilType.label,
                color = StarveilColors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(StarveilSpacing.xs))
                    .clickable(role = Role.Button, onClick = onEdit)
                    .padding(horizontal = StarveilSpacing.sm, vertical = StarveilSpacing.xs),
            )
            Text(
                text = stringResource(R.string.rules_delete),
                style = StarveilType.label,
                color = StarveilColors.ink2,
                modifier = Modifier
                    .clip(RoundedCornerShape(StarveilSpacing.xs))
                    .clickable(role = Role.Button, onClick = onDelete)
                    .padding(horizontal = StarveilSpacing.sm, vertical = StarveilSpacing.xs),
            )
            // The arrows move one place; a rule at the bottom of a long list
            // needs nineteen of them. These two are the one-action version,
            // and they are disabled at the ends rather than hidden so the row
            // does not change width as the rule moves.
            Text(
                text = stringResource(R.string.rules_move_top),
                style = StarveilType.label,
                color = if (canMoveUp) StarveilColors.accent else StarveilColors.ink3.copy(alpha = 0.4f),
                modifier = Modifier
                    .clip(RoundedCornerShape(StarveilSpacing.xs))
                    .clickable(enabled = canMoveUp, role = Role.Button, onClick = onMoveTop)
                    .padding(horizontal = StarveilSpacing.sm, vertical = StarveilSpacing.xs),
            )
            Text(
                text = stringResource(R.string.rules_move_bottom),
                style = StarveilType.label,
                color = if (canMoveDown) StarveilColors.accent else StarveilColors.ink3.copy(alpha = 0.4f),
                modifier = Modifier
                    .clip(RoundedCornerShape(StarveilSpacing.xs))
                    .clickable(enabled = canMoveDown, role = Role.Button, onClick = onMoveBottom)
                    .padding(horizontal = StarveilSpacing.sm, vertical = StarveilSpacing.xs),
            )
            Spacer(modifier = Modifier.weight(1f))
            StarveilIconAction(
                icon = StarveilIcons.Up,
                contentDescription = stringResource(R.string.rules_move_up),
                onClick = onMoveUp,
                enabled = canMoveUp,
                tint = if (canMoveUp) StarveilColors.ink2 else StarveilColors.ink3.copy(alpha = 0.4f),
            )
            StarveilIconAction(
                icon = StarveilIcons.Down,
                contentDescription = stringResource(R.string.rules_move_down),
                onClick = onMoveDown,
                enabled = canMoveDown,
                tint = if (canMoveDown) StarveilColors.ink2 else StarveilColors.ink3.copy(alpha = 0.4f),
            )
        }
    }
}

/** The order column: wide enough for three digits, which is a long list. */
private val ORDER_COLUMN_WIDTH = 34.dp

/** How wide the "there is more" fade is at the end of a scrolling row. */
private val MATCHER_FADE = 32.dp

/**
 * The last thing evaluated: traffic no rule matched.
 *
 * Rendered as a row so the list has an end, but with no switch and no actions —
 * it is a property of the preset, and pretending it is editable would be a lie.
 */
@Composable
private fun FallbackRow(position: Int, action: RuleAction) {
    Row(
        modifier = Modifier.padding(
            start = StarveilSpacing.lg,
            end = StarveilSpacing.md,
            top = StarveilSpacing.md,
            bottom = StarveilSpacing.md,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#$position",
            style = StarveilType.tech,
            color = StarveilColors.ink3,
            modifier = Modifier.width(ORDER_COLUMN_WIDTH),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.rules_fallback_title),
                style = StarveilType.headline,
                color = StarveilColors.ink,
            )
            Text(
                text = actionLabel(action),
                style = StarveilType.label,
                color = StarveilColors.ink2,
            )
        }
    }
}



/**
 * Recent decisions, grouped: which rule sent traffic where, and how often.
 *
 * The list used to show five connections and push the rules off the screen.
 * Fifty connections are rarely fifty different answers — grouping is what
 * makes them fit, and the count is the signal: the rule that matched 40 of 50
 * is the rule that explains the tunnel.
 */
@Composable
private fun FlowSection(
    records: List<FlowRecord>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenAll: () -> Unit,
) {
    val groups = remember(records) { groupFlow(records) }
    val shown = if (expanded) groups else groups.take(GROUP_ROWS)

    StarveilCard {
        Row(
            modifier = Modifier.padding(
                start = StarveilSpacing.lg,
                end = StarveilSpacing.md,
                top = StarveilSpacing.md,
                bottom = StarveilSpacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.rules_flow_title),
                style = StarveilType.headline,
                color = StarveilColors.ink,
                modifier = Modifier.weight(1f),
            )
            if (records.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.rules_flow_view_all, records.size),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(StarveilSpacing.xs))
                        .clickable(role = Role.Button, onClick = onOpenAll)
                        .padding(horizontal = StarveilSpacing.sm, vertical = StarveilSpacing.xs),
                )
            }
        }
        if (records.isEmpty()) {
            Text(
                text = stringResource(R.string.rules_flow_empty),
                style = StarveilType.label,
                color = StarveilColors.ink2,
                modifier = Modifier.padding(
                    start = StarveilSpacing.lg,
                    end = StarveilSpacing.lg,
                    bottom = StarveilSpacing.lg,
                ),
            )
        } else {
            shown.forEachIndexed { index, group ->
                if (index > 0) StarveilDivider(startInset = StarveilSpacing.lg)
                FlowGroupRow(group)
            }
            if (groups.size > GROUP_ROWS) {
                StarveilDivider(startInset = StarveilSpacing.lg)
                Text(
                    text = stringResource(
                        if (expanded) R.string.rules_flow_collapse else R.string.rules_flow_expand,
                        groups.size,
                    ),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(StarveilSpacing.xs))
                        .clickable(role = Role.Button, onClick = onToggleExpanded)
                        .padding(horizontal = StarveilSpacing.lg, vertical = StarveilSpacing.md),
                )
            }
        }
    }
}

/** One summary line: the rule, where it sent traffic, and how many times. */
@Composable
private fun FlowGroupRow(group: FlowGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = StarveilSpacing.lg,
                end = StarveilSpacing.lg,
                top = StarveilSpacing.md,
                bottom = StarveilSpacing.md,
            ),
        // Baseline, not centre: the label, the count and the outcome are three
        // different type sizes, and centring them makes the number float.
    ) {
        // The rule's own name, in the mono face: it is an identifier the user
        // typed, not prose.
        Text(
            text = group.rule.ifBlank { stringResource(R.string.rules_flow_default_rule) },
            style = StarveilType.label.copy(fontFamily = MonoFamily),
            color = StarveilColors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
        Spacer(modifier = Modifier.width(StarveilSpacing.md))
        Text(
            text = group.count.toString(),
            style = StarveilType.numInline.copy(fontFeatureSettings = "tnum"),
            color = StarveilColors.ink,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(modifier = Modifier.width(StarveilSpacing.sm))
        Text(
            text = stringResource(group.outcome.labelRes()),
            style = StarveilType.label,
            color = group.outcome.colour(),
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * Every decision, densely, with a search and a scroll that pauses the feed.
 *
 * Pausing is not a preference: a list that reorders under a moving finger is
 * unreadable, so while the list is scrolling it shows the snapshot taken when
 * the scroll began and picks the live feed back up when it stops.
 *
 * Rows are already clickable; the handler is empty until Tap-to-Route lands
 * (docs/16), so the gesture does not have to be retrofitted later.
 */
@Composable
private fun AllFlowDialog(
    records: List<FlowRecord>,
    onDismiss: () -> Unit,
    onRecordClick: (FlowRecord) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            var query by remember { mutableStateOf("") }
            val listState = rememberLazyListState()
            val latest by rememberUpdatedState(records)
            var frozen by remember { mutableStateOf<List<FlowRecord>>(emptyList()) }
            var paused by remember { mutableStateOf(false) }
            // Pause on movement, not on "isScrollInProgress": the latter is
            // only true while the gesture is live, which is exactly when a
            // screenshot cannot be taken — and a list that resumes the instant
            // the finger lifts still jumps under the reader. Any change of the
            // first visible item freezes the feed, and it stays frozen for a
            // beat after the last change.
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                    .drop(1)
                    .collectLatest {
                        frozen = latest
                        paused = true
                        delay(PAUSE_AFTER_SCROLL_MS)
                        paused = false
                    }
            }
            val source = if (paused) frozen else records
            val needle = query.trim()
            val filtered = remember(source, needle) {
                if (needle.isEmpty()) {
                    source
                } else {
                    source.filter { record ->
                        record.label.contains(needle, ignoreCase = true) ||
                            record.destination.contains(needle, ignoreCase = true) ||
                            record.rule.contains(needle, ignoreCase = true)
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = StarveilSpacing.lg,
                            top = StarveilSpacing.lg,
                            end = StarveilSpacing.sm,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.rules_flow_title),
                        style = StarveilType.title,
                        color = StarveilColors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    if (paused) {
                        Text(
                            text = stringResource(R.string.rules_flow_paused),
                            style = StarveilType.tech,
                            color = StarveilColors.ink2,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.rules_flow_close))
                    }
                }
                StarveilTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.rules_flow_search_hint),
                                        modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = StarveilSpacing.lg, vertical = StarveilSpacing.sm),
                )
                if (filtered.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.rules_flow_no_match_title),
                        body = stringResource(R.string.rules_flow_no_match_body),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        // weight, not fillMaxSize: inside a Column the latter
                        // measures against the parent's full height, so the
                        // viewport extended past the window and the list
                        // believed all 40 rows already fit — it could not
                        // scroll, and the last rows were unreachable.
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = StarveilSpacing.lg,
                            end = StarveilSpacing.lg,
                            bottom = StarveilSpacing.lg,
                        ),
                    ) {
                        items(filtered, key = { it.connectionId }) { record ->
                            DenseFlowRow(record, onClick = { onRecordClick(record) })
                        }
                    }
                }
            }
        }
    }
}

/** One line: time, what was asked for, which rule, where it went. */
@Composable
private fun DenseFlowRow(record: FlowRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.xs),
    ) {
        Text(
            text = FLOW_TIME.format(
                Instant.ofEpochMilli(record.createdAtMs).atZone(ZoneId.systemDefault()),
            ),
            style = StarveilType.tech,
            fontFamily = FontFamily.Monospace,
            color = StarveilColors.ink2,
        )
        Text(
            text = record.label,
            style = StarveilType.label,
            color = StarveilColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = record.rule.ifBlank { stringResource(R.string.rules_flow_default_rule) },
            style = StarveilType.tech,
            color = StarveilColors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.3f),
        )
        Text(
            text = stringResource(record.outcome.labelRes()),
            style = StarveilType.tech,
            color = record.outcome.colour(),
        )
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
    FlowOutcome.PROXY -> StarveilColors.accent
    FlowOutcome.DIRECT -> StarveilColors.ok
    FlowOutcome.BLOCK -> StarveilColors.errText
}

/** Keys and counts for the flow section. */
private const val FLOW_HEADER_KEY = "flow-header"
private const val BEGINNER_KEY = "beginner"
private const val PRESET_KEY = "mainland-preset"

/** Matches the node card on the Nodes screen; the two are the same kind of thing. */
/**
 * How many groups the summary shows before "show all".
 *
 * The adapter keeps a ring of 100; this is what is glanceable. The rest are one
 * tap away in the full list, which is where 100 rows belong.
 */
private const val GROUP_ROWS = 5

/** How long the feed stays frozen after the list stops moving. */
private const val PAUSE_AFTER_SCROLL_MS = 900L

/** The same shape the log viewer uses, so two lists of times read alike. */
private val FLOW_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * The example for the value field, per matcher.
 *
 * These are not decoration: `domain` is an exact host and `domain_suffix` is
 * that host plus everything under it, so the same string means two different
 * matches. The examples come from the fields sing-box actually receives (see
 * `ConfigBuilder`, which writes `kind.wireName to listOf(value)`).
 */
@Composable
internal fun kindLabel(kind: MatcherKind): String = stringResource(
    when (kind) {
        MatcherKind.DOMAIN -> R.string.rules_kind_domain
        MatcherKind.DOMAIN_SUFFIX -> R.string.rules_kind_domain_suffix
        MatcherKind.DOMAIN_KEYWORD -> R.string.rules_kind_domain_keyword
        MatcherKind.IP_CIDR -> R.string.rules_kind_ip_cidr
    },
)

@Composable
internal fun actionLabel(action: RuleAction): String = stringResource(
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
    subscribedPacks: Set<String>,
    availablePacks: List<RulePack>,
    onSelect: (RoutePreset) -> Unit,
    onToggleAds: (Boolean) -> Unit,
    onOpenMarket: () -> Unit,
    onOpenPerApp: () -> Unit,
    onOpenScenes: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    StarveilCard {
        // The policy: three choices, one of them in force.
        PresetRow(
            title = stringResource(R.string.rules_preset_title),
            description = stringResource(R.string.rules_preset_desc),
            selected = preset == RoutePreset.BYPASS_MAINLAND,
            onSelect = { onSelect(RoutePreset.BYPASS_MAINLAND) },
        )
        StarveilDivider(startInset = TEXT_COLUMN_INSET)
        PresetRow(
            title = stringResource(R.string.rules_preset_proxy_title),
            description = stringResource(R.string.rules_preset_proxy_desc),
            selected = preset == RoutePreset.GLOBAL_PROXY,
            onSelect = { onSelect(RoutePreset.GLOBAL_PROXY) },
        )
        StarveilDivider(startInset = TEXT_COLUMN_INSET)
        PresetRow(
            title = stringResource(R.string.rules_preset_direct_title),
            description = stringResource(R.string.rules_preset_direct_desc),
            selected = preset == RoutePreset.GLOBAL_DIRECT,
            onSelect = { onSelect(RoutePreset.GLOBAL_DIRECT) },
        )
        // A footer of the card, not a footnote to the last option: this rule is
        // built in and applies to every preset, and sitting it directly under
        // "Global direct" made it read as part of that choice.
        StarveilDivider(startInset = TEXT_COLUMN_INSET)
        Text(
            text = stringResource(R.string.rules_lan_note),
            style = StarveilType.label,
            color = StarveilColors.ink2,
            modifier = Modifier.padding(
                start = TEXT_COLUMN_INSET,
                end = StarveilSpacing.lg,
                top = StarveilSpacing.xs,
                bottom = StarveilSpacing.lg,
            ),
        )
        // Persistent, not only the Snackbar: the condition lasts until a
        // verified download succeeds, and the counts below would otherwise go
        // on claiming rules that are not in the config.
        if (failed) {
            Text(
                text = stringResource(R.string.rules_ruleset_failed),
                style = StarveilType.label,
                color = StarveilColors.errText,
                modifier = Modifier.padding(
                    start = TEXT_COLUMN_INSET,
                    end = StarveilSpacing.lg,
                    bottom = StarveilSpacing.md,
                ),
            )
        }

        StarveilDivider(startInset = StarveilSpacing.lg)

        // The ad switch, in the same card: it is part of the policy, not a
        // separate setting that happens to live nearby.
        Row(
            modifier = Modifier.padding(
                start = StarveilSpacing.lg,
                end = StarveilSpacing.md,
                top = StarveilSpacing.md,
                bottom = StarveilSpacing.md,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rules_block_ads_title),
                    style = StarveilType.headline,
                    color = StarveilColors.ink,
                )
                Text(
                    text = stringResource(R.string.rules_block_ads_desc),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                )
            }
            StarveilSwitch(checked = blockAds, onCheckedChange = onToggleAds)
        }

        StarveilDivider(startInset = StarveilSpacing.lg)

        // The entries: one card, one column, the value on the right.
        EntryRow(
            title = stringResource(R.string.rules_market_title),
            description = stringResource(R.string.rules_market_desc),
            // One line: Chinese wraps between any two characters, so a column
            // narrower than the sentence split it mid-word ("哈 / 希").
            descriptionMaxLines = 1,
            value = stringResource(R.string.rules_market_subscribed, subscribedPacks.size),
            onClick = onOpenMarket,
        )
        StarveilDivider(startInset = StarveilSpacing.lg)
        EntryRow(
            title = stringResource(R.string.rules_per_app_title),
            description = stringResource(R.string.rules_per_app_desc),
            onClick = onOpenPerApp,
        )
        StarveilDivider(startInset = StarveilSpacing.lg)
        EntryRow(
            title = stringResource(R.string.rules_scenes_title),
            description = stringResource(R.string.rules_scenes_desc),
            onClick = onOpenScenes,
        )
        StarveilDivider(startInset = StarveilSpacing.lg)
        // What the kernel will actually be told, not what the switches look
        // like: a preset that could not download its files degrades at connect
        // time, and this is where the user would notice.
        val systemRules = presetRules.size + subscribedPacks.size
        EntryRow(
            title = stringResource(R.string.rules_effective_count_title),
            description = if (userRuleCount == 0) {
                stringResource(R.string.rules_effective_count_system, systemRules)
            } else {
                stringResource(
                    R.string.rules_effective_count,
                    systemRules + userRuleCount,
                    systemRules,
                    userRuleCount,
                )
            },
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            Column(
                modifier = Modifier.padding(
                    start = StarveilSpacing.lg,
                    end = StarveilSpacing.lg,
                    bottom = StarveilSpacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.xs),
            ) {
                presetRules.forEach { rule ->
                    Text(
                        text = stringResource(
                            when (rule) {
                                PresetRule.ADS -> R.string.rules_effective_ads
                                PresetRule.LAN_DIRECT -> R.string.rules_effective_lan
                                PresetRule.DOMESTIC_NAMES -> R.string.rules_effective_domestic_names
                                PresetRule.DOMESTIC_ADDRESSES -> R.string.rules_effective_domestic_addresses
                            },
                        ),
                        style = StarveilType.label,
                        color = StarveilColors.ink2,
                    )
                }
                // Named, not counted: "3 rules in force" answers how many, and
                // the next question is which pack is doing it.
                availablePacks.filter { it.id in subscribedPacks }.forEach { pack ->
                    Text(
                        text = stringResource(
                            R.string.rules_effective_pack,
                            packTitle(LocalContext.current, pack.id),
                            stringResource(
                                if (pack.action == RulePackAction.REJECT) {
                                    R.string.rules_action_block
                                } else {
                                    R.string.rules_action_proxy
                                },
                            ),
                        ),
                        style = StarveilType.label,
                        color = StarveilColors.ink2,
                    )
                }
                if (userRuleCount > 0) {
                    Text(
                        text = stringResource(R.string.rules_effective_user, userRuleCount),
                        style = StarveilType.label,
                        color = StarveilColors.ink2,
                    )
                }
            }
        }
    }
}

/** A row that opens something, with its value and a chevron on the right. */
@Composable
private fun EntryRow(
    title: String,
    description: String? = null,
    descriptionMaxLines: Int = 2,
    value: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                start = StarveilSpacing.lg,
                end = StarveilSpacing.lg,
                top = StarveilSpacing.md,
                bottom = StarveilSpacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = StarveilType.headline, color = StarveilColors.ink)
            if (description != null) {
                Text(
                    text = description,
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                    maxLines = descriptionMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Text(text = value, style = StarveilType.label, color = StarveilColors.accent)
            Spacer(modifier = Modifier.width(StarveilSpacing.sm))
        }
        Icon(
            imageVector = StarveilIcons.Chevron,
            contentDescription = null,
            tint = StarveilColors.ink3,
            modifier = Modifier.size(18.dp),
        )
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
            .padding(
                start = StarveilSpacing.lg,
                end = StarveilSpacing.lg,
                top = StarveilSpacing.md,
                bottom = StarveilSpacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The mark is an accent ring with an accent dot, or an empty ring: a
        // Material radio button would bring its own tint and its own size.
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(
                        1.5.dp,
                        if (selected) StarveilColors.accent else StarveilColors.ink3,
                        CircleShape,
                    ),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(StarveilColors.accent),
                )
            }
        }
        Spacer(modifier = Modifier.width(StarveilSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = StarveilType.headline, color = StarveilColors.ink)
            Text(text = description, style = StarveilType.label, color = StarveilColors.ink2)
        }
    }
}

/** Where a preset row's text starts: the card's padding, the mark, the gap. */
private val TEXT_COLUMN_INSET = 48.dp

/**
 * What a new user gets before they touch anything.
 *
 * Not a modal and not a separate screen: it sits at the top of the rules page,
 * says what is already in force in three lines, and gets out of the way with
 * one tap. The controls underneath stay live — this is an explanation, not a
 * mode the app enforces.
 */
@Composable
private fun BeginnerCard(onCustomize: () -> Unit) {
    StarveilCard {
        Column(modifier = Modifier.padding(StarveilSpacing.lg)) {
            Text(
                text = stringResource(R.string.rules_beginner_title),
                style = StarveilType.headline,
                color = StarveilColors.ink,
            )
            Spacer(modifier = Modifier.height(StarveilSpacing.sm))
            listOf(
                R.string.rules_beginner_domestic,
                R.string.rules_beginner_abroad,
                R.string.rules_beginner_ads,
            ).forEach { line ->
                Text(
                    text = "· " + stringResource(line),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(StarveilSpacing.sm))
            Text(
                text = stringResource(R.string.rules_beginner_customize),
                style = StarveilType.label,
                color = StarveilColors.accentText,
                modifier = Modifier
                    .clip(RoundedCornerShape(StarveilSpacing.xs))
                    .clickable(role = Role.Button, onClick = onCustomize)
                    .padding(vertical = StarveilSpacing.xs),
            )
        }
    }
}

/** How long a freshly created rule stays highlighted. */
private const val HIGHLIGHT_MS = 1200L
