package com.justnpcx.starveil.feature.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.model.AvailabilityBand
import com.justnpcx.starveil.core.model.NodeHealth
import com.justnpcx.starveil.core.model.RecommendationReason
import com.justnpcx.starveil.core.ui.components.ConnectionCore
import com.justnpcx.starveil.core.ui.components.ConnectionCoreGeometry
import com.justnpcx.starveil.core.ui.components.ConnectionState
import com.justnpcx.starveil.core.ui.components.NodeHealthChart
import com.justnpcx.starveil.core.ui.components.StarveilBadge
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Dashboard — the cockpit. Tap the core to connect/disconnect.
 *
 * The page is a ring, two rates, and two glass cards. Nothing is boxed that
 * does not need an edge: the rates are the page's own type, and the cards hold
 * the things that are objects — the node, and the node's health.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    /**
     * Asks for VPN consent before connecting. Supplied by :app (which owns the
     * activity-result launcher); the default assumes consent is already held,
     * so previews and tests do not need the port.
     */
    requestVpnConsent: (onGranted: () -> Unit) -> Unit = { it() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // The notice whose details are open, if any. Held here rather than in the
    // view model: it is whether a dialog is showing, not what the app knows.
    var details by remember { mutableStateOf<DashboardNotice?>(null) }

    // A persistent notice is a condition that lasts until something changes —
    // it gets the fixed slot. Everything else is a one-off confirmation and
    // goes to a Snackbar, so it can never push the layout around.
    val notice = state.notice
    val noticeText = notice?.text()
    val persistent = notice?.isPersistent() == true
    LaunchedEffect(notice) {
        if (noticeText != null && !persistent) {
            snackbar.showSnackbar(noticeText, duration = SnackbarDuration.Short)
        }
    }

    // Landscape cannot hold the column, so it scrolls. Portrait must not.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        val scroll = if (landscape) Modifier.verticalScroll(rememberScrollState()) else Modifier

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(scroll)
                .padding(horizontal = StarveilSpacing.pageMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 32dp of air on the grid: the ring starts below the status bar
            // rather than floating in the middle of an empty top third.
            Spacer(modifier = Modifier.height(StarveilSpacing.xxl))
            ConnectionCore(
                state = state.coreState,
                busy = state.isBusy,
                // Fixed in every state: everything below is measured from this
                // box, so a state change must not move any of it.
                diameter = ConnectionCoreGeometry.diameterDp.dp,
                // 18 dp of the ring's inset is empty; the numbers move into it.
                bottomTrim = 18.dp,
                label = stringResource(
                    when (state.coreState) {
                        ConnectionState.DISCONNECTED -> R.string.dashboard_state_disconnected
                        ConnectionState.CONNECTING -> R.string.dashboard_state_connecting
                        ConnectionState.CONNECTED -> R.string.dashboard_state_connected
                        ConnectionState.BLOCKED -> R.string.dashboard_state_blocked
                        ConnectionState.WARNING -> R.string.dashboard_state_warning
                    },
                ),
                onClick = { requestVpnConsent { viewModel.onCoreClick() } },
                // The core is the screen's primary control, so its description
                // carries both the state and what tapping does (§6.1).
                contentDescription = stringResource(
                    when (state.coreState) {
                        ConnectionState.DISCONNECTED -> R.string.dashboard_core_disconnected
                        ConnectionState.CONNECTING -> R.string.dashboard_core_connecting
                        ConnectionState.CONNECTED -> R.string.dashboard_core_connected
                        ConnectionState.WARNING -> R.string.dashboard_core_warning
                        ConnectionState.BLOCKED -> R.string.dashboard_core_blocked
                    },
                ),
            )

            SpeedSection(state = state, modifier = Modifier.height(TELEMETRY_HEIGHT))

            // The context line sits under the group it explains, in a fixed
            // slot, so a state change cannot move anything below it. A failure
            // that names a reason is tappable: the slot shows one line, and the
            // line is the only place the kernel's own words appear.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CONTEXT_SLOT_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                if (persistent && noticeText != null) {
                    val expandable = notice is DashboardNotice.KernelRefused
                    Text(
                        text = noticeText,
                        style = StarveilType.label,
                        color = when (notice) {
                            DashboardNotice.KillSwitchBlocking -> StarveilColors.err
                            else -> StarveilColors.warn
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (expandable) {
                            Modifier
                                .clip(RoundedCornerShape(StarveilSpacing.xs))
                                .clickable { details = notice }
                                .padding(horizontal = StarveilSpacing.xs)
                        } else {
                            Modifier
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(StarveilSpacing.xl))

            CurrentNodeCard(
                name = state.selectedNodeName ?: stringResource(R.string.dashboard_no_node),
                health = state.health,
                onAutoSelect = viewModel::autoSelect,
            )

            // §7.2: the selected node's health over the window. Only for a
            // node — a group has no single curve — and the chart says
            // "collecting" in words until there is a shape worth drawing.
            state.health?.let { health ->
                Spacer(modifier = Modifier.height(StarveilSpacing.md))
                NodeHealthSection(
                    health = health,
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    chartHeight = if (landscape) 160.dp else null,
                )
            }

            // The last card must not touch the navigation bar.
            Spacer(modifier = Modifier.height(StarveilSpacing.xxl))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        details?.let { notice ->
            FailureDetailsDialog(notice = notice, onDismiss = { details = null })
        }
    }
}

/**
 * The whole failure, and a way to keep it.
 *
 * One line in the context slot is enough to notice something went wrong; it is
 * not enough to act on, and the reason is the part a bug report needs. Copying
 * is the point of this dialog.
 */
@Composable
private fun FailureDetailsDialog(notice: DashboardNotice, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = notice.text()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_failure_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                Text(text, style = StarveilType.body)
                Text(
                    text = stringResource(R.string.dashboard_failure_hint),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Starveil", text))
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.dashboard_failure_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dashboard_failure_close))
            }
        },
    )
}

/**
 * §7.2's health history, in a glass card: the curve, its availability, and the
 * line that says what the area is.
 */
@Composable
private fun NodeHealthSection(
    health: NodeHealth,
    modifier: Modifier = Modifier,
    /** Non-null only in landscape, where weight() has no bounded height. */
    chartHeight: Dp? = null,
) {
    StarveilCard(modifier = modifier) {
        Column(modifier = Modifier.padding(StarveilSpacing.lg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HEALTH_HEADER_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_health_title),
                    style = StarveilType.headline,
                    color = StarveilColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!health.collecting) {
                    // The bar's colour is the band; the number makes it readable
                    // without having to judge a shade — and colour alone is not
                    // a signal for everyone.
                    Text(
                        text = stringResource(
                            R.string.dashboard_health_availability,
                            health.availabilityPct.roundToInt(),
                        ),
                        style = StarveilType.label,
                        color = when (health.band) {
                            AvailabilityBand.GOOD -> StarveilColors.ok
                            AvailabilityBand.FAIR -> StarveilColors.warn
                            AvailabilityBand.POOR -> StarveilColors.err
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(StarveilSpacing.sm))
            val reachable = health.samples.filter { it.reachable }
            NodeHealthChart(
                health = health,
                height = chartHeight,
                contentDescription = if (reachable.isEmpty()) {
                    stringResource(R.string.dashboard_health_chart_empty)
                } else {
                    stringResource(
                        R.string.dashboard_health_chart_desc,
                        reachable.size,
                        reachable.minOf { it.latencyMs },
                        reachable.maxOf { it.latencyMs },
                    )
                },
                modifier = if (chartHeight == null) {
                    // The chart absorbs whatever the fixed blocks left.
                    Modifier.fillMaxWidth().heightIn(min = HEALTH_MIN_HEIGHT)
                } else {
                    Modifier.fillMaxWidth()
                },
            )
        }
    }
}

/**
 * A notice as the user reads it.
 *
 * The one place the dashboard's copy lives, so the view model can stay free of
 * it (C1) and the wording can be reviewed with the rest of the app's.
 */
@Composable
private fun DashboardNotice.text(): String = when (this) {
    DashboardNotice.KillSwitchBlocking -> stringResource(R.string.dashboard_notice_kill_switch)
    DashboardNotice.KillSwitchFailed -> stringResource(R.string.dashboard_notice_kill_switch_failed)
    DashboardNotice.TunnelDropped -> stringResource(R.string.dashboard_notice_tunnel_dropped)
    DashboardNotice.TunnelReconnecting -> stringResource(R.string.dashboard_notice_tunnel_reconnecting)
    DashboardNotice.TunnelKeepsDropping ->
        stringResource(R.string.dashboard_notice_tunnel_keeps_dropping)
    DashboardNotice.NoTarget -> stringResource(R.string.dashboard_notice_no_target)
    DashboardNotice.ConfigUnbuildable -> stringResource(R.string.dashboard_notice_config)
    // The kernel's own reason is the useful part; without one, say only that it
    // refused rather than inventing a detail.
    is DashboardNotice.KernelRefused -> reason
        ?.let { stringResource(R.string.dashboard_notice_kernel_refused_reason, it) }
        ?: stringResource(R.string.dashboard_notice_kernel_refused)
    is DashboardNotice.AutoSelected ->
        stringResource(R.string.dashboard_notice_auto_selected, nodeName, reason.text())
    DashboardNotice.SwitchingNode ->
        stringResource(R.string.dashboard_notice_switching_node)
    DashboardNotice.TunnelSilent ->
        stringResource(R.string.dashboard_notice_tunnel_silent)
}

/**
 * The picker's reason, as a clause.
 *
 * A clause rather than a sentence because it is used as the second half of the
 * "auto-selected X — Y" notice.
 */
@Composable
private fun RecommendationReason.text(): String = when (this) {
    is RecommendationReason.Healthiest -> healthClause(availabilityPct, latencyMs)
    is RecommendationReason.TiedOnScore -> region
        ?.let { stringResource(R.string.dashboard_reason_tied_region, healthClause(availabilityPct, latencyMs), it.displayName) }
        ?: stringResource(R.string.dashboard_reason_tied, healthClause(availabilityPct, latencyMs))
    is RecommendationReason.AllCoolingDown ->
        stringResource(R.string.dashboard_reason_cooling_down, minutesLeft)
    RecommendationReason.NothingMeasured ->
        stringResource(R.string.dashboard_reason_unmeasured)
}

/**
 * What the picker saw, in the units the user measures in.
 *
 * The score is a 0–10099 internal number. It is deliberately not shown:
 * "score 10099" is precise about the wrong thing, and the two numbers it is
 * made of are the ones a person can check against the health chart above.
 */
@Composable
private fun healthClause(availabilityPct: Int, latencyMs: Long?): String =
    latencyMs?.let { stringResource(R.string.dashboard_reason_health, availabilityPct, it) }
        ?: stringResource(R.string.dashboard_reason_health_no_latency, availabilityPct)

/**
 * The telemetry block: two columns, mirrored about the screen's centre.
 *
 * Each column is label / number / total, top to bottom, and the three rows line
 * up across the gap: both labels on one baseline, both numbers on the next, both
 * totals on the third. That is what makes the pair read as one instrument
 * rather than as two stacks that happen to be side by side.
 *
 * The unit sits on the number's baseline, not above it.
 *
 * Dimmed as a whole when there is no tunnel: three zeros say "no traffic", a
 * third of the ink says "nothing is connected", and the second is the truth.
 */
@Composable
private fun SpeedSection(state: DashboardUiState, modifier: Modifier = Modifier) {
    val dim = state.coreState == ConnectionState.DISCONNECTED
    val down = formatRateParts(state.stats.downlinkRate)
    val up = formatRateParts(state.stats.uplinkRate)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (dim) 0.38f else 1f),
        verticalAlignment = Alignment.Top,
    ) {
        SpeedColumn(
            label = stringResource(R.string.dashboard_speed_down),
            parts = down,
            total = state.stats.downlinkBytes,
            modifier = Modifier.weight(1f),
        )
        SpeedColumn(
            label = stringResource(R.string.dashboard_speed_up),
            parts = up,
            total = state.stats.uplinkBytes,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SpeedColumn(
    label: String,
    parts: RateParts,
    total: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = StarveilType.tech,
            color = StarveilColors.ink2,
        )
        Row(
            // alignByBaseline on both children, so the unit sits on the digits'
            // baseline whatever size either of them is.
            modifier = Modifier.padding(top = StarveilSpacing.sm),
        ) {
            Text(
                text = parts.value,
                // Tabular figures: a rate that ticks every second must not
                // change width, or the whole row twitches with it.
                style = StarveilType.display.copy(fontFeatureSettings = "tnum"),
                color = StarveilColors.ink,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = parts.unit,
                style = StarveilType.tech.copy(fontSize = 13.sp),
                color = StarveilColors.ink2,
                modifier = Modifier
                    .alignByBaseline()
                    .padding(start = 6.dp),
            )
        }
        Text(
            text = stringResource(R.string.dashboard_speed_total, formatBytes(total)),
            style = StarveilType.label,
            color = StarveilColors.ink2,
            modifier = Modifier.padding(top = StarveilSpacing.xs),
        )
    }
}

/** A rate split so the number and its unit can be sized independently. */
internal data class RateParts(val value: String, val unit: String)

internal fun formatRateParts(bytesPerSecond: Long): RateParts =
    formatScaledParts(bytesPerSecond.toDouble(), RATE_UNITS)

/**
 * [value] in the largest unit that keeps it readable, with the unit separate.
 *
 * The mantissa never exceeds four characters: the unit is chosen from the
 * **rounded** value, so 999.6 KB becomes 1.0 MB rather than "1000 KB". That is
 * what stops a long number from overlapping its neighbour in a 50% column.
 */
private fun formatScaledParts(value: Double, units: List<String>): RateParts {
    var scaled = value
    for (index in units.indices) {
        val decimals = if (index <= 1) 0 else 1
        val rounded = roundTo(scaled, decimals)
        if (index == units.lastIndex || rounded < SCALE) {
            val text = if (decimals == 0) {
                rounded.toLong().toString()
            } else {
                String.format(Locale.US, "%.1f", rounded)
            }
            return RateParts(text, units[index])
        }
        scaled /= SCALE
    }
    return RateParts(value.toLong().toString(), units.first())
}

/**
 * The node the next connect will use, with the one-tap pick beside it.
 *
 * A glass card, because the node is an object and the page needs one thing with
 * an edge above the health curve. The badge sits in the name's row — it
 * modifies the node, so it belongs to that line.
 */
@Composable
private fun CurrentNodeCard(
    name: String,
    health: NodeHealth?,
    onAutoSelect: () -> Unit,
) {
    StarveilCard {
        Column(modifier = Modifier.padding(StarveilSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The name carries its country's flag as a leading emoji; set it
                // at 22sp and the rest at the row's own size, so the flag is an
                // element rather than the first glyph of a word.
                NodeName(name = name, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(StarveilSpacing.md))
                AutoSelectBadge(onClick = onAutoSelect)
            }
            // Latency only: availability is 100% for the node you are connected
            // to, so repeating it here says nothing. The health card keeps it,
            // where it is the window's summary.
            health?.takeIf { !it.collecting }?.emaMs?.let { latency ->
                Spacer(modifier = Modifier.height(StarveilSpacing.xs))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = stringResource(R.string.dashboard_latency_label),
                        style = StarveilType.tech,
                        color = StarveilColors.ink2,
                    )
                    Spacer(modifier = Modifier.width(StarveilSpacing.sm))
                    Text(
                        text = latency.toString(),
                        style = StarveilType.numInline,
                        color = StarveilColors.ink,
                    )
                    Text(
                        text = " ms",
                        style = StarveilType.tech,
                        color = StarveilColors.ink2,
                    )
                }
            }
        }
    }
}

/**
 * A node's name, with a leading flag at 22sp.
 *
 * `Profile.displayName()` puts the flag in the string, and a flag at 17sp reads
 * as punctuation. Splitting it out is a display rule, not a data one: nothing
 * outside this function knows the flag was ever separate.
 */
@Composable
private fun NodeName(name: String, modifier: Modifier = Modifier) {
    val (flag, rest) = splitLeadingFlag(name)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (flag != null) {
            Text(
                text = flag,
                fontSize = 22.sp,
                color = StarveilColors.ink,
            )
            Spacer(modifier = Modifier.width(StarveilSpacing.sm))
        }
        Text(
            text = rest,
            style = StarveilType.headline,
            color = StarveilColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Splits a leading pair of regional-indicator symbols off a name.
 *
 * Returns (flag, rest), or (null, name) when the name does not start with one.
 * The pair is two code points; a name that begins with a single one is not a
 * flag and is left alone.
 */
internal fun splitLeadingFlag(name: String): Pair<String?, String> {
    val points = name.codePoints().toArray()
    if (points.size < 3) return null to name
    val first = points[0]
    val second = points[1]
    val isIndicator = { cp: Int -> cp in 0x1F1E6..0x1F1FF }
    if (!isIndicator(first) || !isIndicator(second)) return null to name
    val flag = String(points, 0, 2)
    val rest = String(points, 2, points.size - 2).trim()
    return flag to rest
}

/**
 * The one-tap pick, as an outlined badge.
 *
 * Outline only, in the accent: one of the three places this screen may spend
 * it, and it is a line plus a word rather than a filled pill. The 48dp touch
 * target is the outer box; the badge itself is what the eye reads as the
 * control.
 */
@Composable
private fun AutoSelectBadge(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StarveilBadge(text = stringResource(R.string.dashboard_auto_select))
    }
}

/** One line of state explanation, occupied or not. */
private val CONTEXT_SLOT_HEIGHT = 24.dp

/** The telemetry block's height, with or without numbers: 16+8+58+4+19, rounded up. */
private val TELEMETRY_HEIGHT = 112.dp

/** The health chart never gets smaller than this. */
private val HEALTH_MIN_HEIGHT = 96.dp

/** The health header is a fixed 32 dp so the chart's share is predictable. */
private val HEALTH_HEADER_HEIGHT = 32.dp

/**
 * A condition that lasts, as opposed to a one-off confirmation.
 *
 * Only these get the slot; everything else is a Snackbar.
 */
private fun DashboardNotice.isPersistent(): Boolean = when (this) {
    // Conditions that last until something changes. A refused start is one:
    // the reason stays true until the next attempt, and it is the only place
    // the kernel's own words appear.
    DashboardNotice.KillSwitchBlocking,
    DashboardNotice.KillSwitchFailed,
    DashboardNotice.NoTarget,
    DashboardNotice.ConfigUnbuildable,
    // Up but carrying nothing: true until traffic arrives or the tunnel goes
    // down, and the whole point is that it must not scroll past.
    DashboardNotice.TunnelSilent,
    is DashboardNotice.KernelRefused,
    -> true

    // One-off: it describes something that is happening, and the state change
    // that ends it clears the notice.
    DashboardNotice.TunnelDropped,
    DashboardNotice.TunnelReconnecting,
    DashboardNotice.TunnelKeepsDropping,
    DashboardNotice.SwitchingNode,
    is DashboardNotice.AutoSelected,
    -> false
}

private val RATE_UNITS = listOf("B/s", "KB/s", "MB/s", "GB/s")
private val BYTE_UNITS = listOf("B", "KB", "MB", "GB")

/** One kibibyte, the step between every pair of units above. */
private const val SCALE = 1024.0

/** [value] rounded to [decimals] places. */
private fun roundTo(value: Double, decimals: Int): Double {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    return round(value * factor) / factor
}

/** A rate with its unit, for copy and for tests. */
internal fun formatRate(bytesPerSecond: Long): String {
    val parts = formatRateParts(bytesPerSecond)
    return parts.value + " " + parts.unit
}

internal fun formatBytes(bytes: Long): String {
    val parts = formatScaledParts(bytes.toDouble(), BYTE_UNITS)
    return "${parts.value} ${parts.unit}"
}
