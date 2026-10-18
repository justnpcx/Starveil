package com.justnpcx.starveil.feature.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
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
import com.justnpcx.starveil.core.ui.components.ConnectionState
import com.justnpcx.starveil.core.ui.components.NodeHealthChart
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * Dashboard — the cockpit. Tap the core to connect/disconnect.
 * Node selector lives HERE (not on a separate page) by design.
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

    // A persistent notice is a condition that lasts until something changes —
    // it gets the fixed slot. Everything else is a one-off confirmation and
    // goes to a Snackbar, so it can never push the layout around (D3).
    val notice = state.notice
    val noticeText = notice?.text()
    val persistent = notice?.isPersistent() == true
    LaunchedEffect(notice) {
        if (noticeText != null && !persistent) {
            snackbar.showSnackbar(noticeText, duration = SnackbarDuration.Short)
        }
    }

    // Landscape cannot hold the column, so it scrolls. Portrait must not (E1).
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The hero is the one elastic element besides the chart: 260 dp when
        // there is room, 220 dp on a short screen, so the fixed blocks below
        // always fit.
        val heroSize = if (maxHeight < 720.dp) 220.dp else 260.dp
        val scroll = if (landscape) Modifier.verticalScroll(rememberScrollState()) else Modifier

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(scroll)
                .padding(horizontal = StarveilSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ConnectionCore(
                state = state.coreState,
                busy = state.isBusy,
                diameter = heroSize,
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

            // F2: the ring and the live numbers are one group — "now" — so
            // they sit 12 dp apart while the cards below keep their distance.
            Spacer(modifier = Modifier.height(12.dp))
            SpeedSection(state = state, modifier = Modifier.height(TELEMETRY_HEIGHT))

            // F3: the context line sits under the group it explains, not
            // between the ring and its numbers. 28 dp, always there, so a
            // state change cannot move anything below it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CONTEXT_SLOT_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                if (persistent && noticeText != null) {
                    Text(
                        text = noticeText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = when (notice) {
                            DashboardNotice.KillSwitchBlocking -> StarveilColors.Blocked
                            else -> StarveilColors.Warning
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            CurrentNodeCard(
                name = state.selectedNodeName ?: stringResource(R.string.dashboard_no_node),
                health = state.health,
                onAutoSelect = viewModel::autoSelect,
            )
            // §7.2: the selected node's health over the window. Only for a
            // node — a group has no single curve — and the chart says
            // "collecting" in words until there is a shape worth drawing.
            state.health?.let { health ->
                Spacer(modifier = Modifier.height(StarveilSpacing.sm))
                NodeHealthSection(
                    health = health,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    chartHeight = if (landscape) 160.dp else null,
                )
            }
            if (landscape) Spacer(modifier = Modifier.height(StarveilSpacing.lg))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * §7.2's health history: the curve, its availability bar and its loss dots,
 * under a line that says what the area is.
 */
@Composable
private fun NodeHealthSection(
    health: NodeHealth,
    modifier: Modifier = Modifier,
    /** Non-null only in landscape, where weight() has no bounded height. */
    chartHeight: Dp? = null,
) {
    // The same card as the node above: same container colour, same rounded
    // shape. The chart used to sit on the bare page, which made the two blocks
    // look like different kinds of thing.
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = StarveilSpacing.lg,
                vertical = StarveilSpacing.sm,
            ),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEALTH_HEADER_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dashboard_health_title),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            // The bar's colour is the band; the number makes it readable
            // without having to judge a shade — and colour alone is not a
            // signal for everyone.
            if (!health.collecting) {
                Text(
                    text = stringResource(
                        R.string.dashboard_health_availability,
                        health.availabilityPct.roundToInt(),
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = when (health.band) {
                        AvailabilityBand.GOOD -> StarveilColors.Connected
                        AvailabilityBand.FAIR -> StarveilColors.Warning
                        AvailabilityBand.POOR -> StarveilColors.Blocked
                    },
                )
            }
        }
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
                // The chart absorbs whatever the fixed blocks left (D4/E1).
                Modifier.weight(1f).fillMaxWidth().heightIn(min = HEALTH_MIN_HEIGHT)
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
 * The score is a 0–10099 internal number (availability in the high digits,
 * latency in the low ones). It is deliberately not shown: "score 10099" is
 * precise about the wrong thing, and the two numbers it is made of are the
 * ones a person can check against the health chart above.
 */
@Composable
private fun healthClause(availabilityPct: Int, latencyMs: Long?): String =
    latencyMs?.let { stringResource(R.string.dashboard_reason_health, availabilityPct, it) }
        ?: stringResource(R.string.dashboard_reason_health_no_latency, availabilityPct)

/**
 * The telemetry block: two columns, each a label, a number, a unit and a
 * ribbon.
 *
 * Dimmed as a whole when there is no tunnel. Three zeros say "no traffic";
 * 35% opacity says "nothing is connected", which is the actual state and the
 * one a glance should read.
 */
@Composable
private fun SpeedSection(state: DashboardUiState, modifier: Modifier = Modifier) {
    val dim = state.coreState == ConnectionState.DISCONNECTED
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (dim) 0.35f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeedCell(
            label = stringResource(R.string.dashboard_speed_down),
            rate = state.stats.downlinkRate,
            total = state.stats.downlinkBytes,
            modifier = Modifier.weight(1f),
        )
        SpeedCell(
            label = stringResource(R.string.dashboard_speed_up),
            rate = state.stats.uplinkRate,
            total = state.stats.uplinkBytes,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SpeedCell(
    label: String,
    rate: Long,
    total: Long,
    modifier: Modifier = Modifier,
) {
    val parts = formatRateParts(rate)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.padding(top = 2.dp)) {
            Text(
                text = parts.value,
                // Tabular figures: a rate that ticks every second must not
                // change width, or the whole row twitches with it.
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = parts.unit,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .alignByBaseline()
                    .padding(start = 3.dp),
            )
        }
        Text(
            text = stringResource(R.string.dashboard_speed_total, formatBytes(total)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
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
 * Three tiers, the same hierarchy the telemetry uses: an overline label, the
 * value, and a supporting line. The button sits in the value's row, not the
 * card's centre — it modifies the node, so it belongs to that line, and
 * centring it over three lines is what made it look like it was floating.
 */
@Composable
private fun CurrentNodeCard(
    name: String,
    health: NodeHealth?,
    onAutoSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(StarveilSpacing.lg)) {
            Text(
                text = stringResource(R.string.dashboard_current_node),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = onAutoSelect,
                    label = {
                        Text(
                            text = stringResource(R.string.dashboard_auto_select),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        )
                    },
                    shape = RoundedCornerShape(50),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
            // Latency only: availability is 100% for the node you are
            // connected to, so repeating it here says nothing (C4). The health
            // card keeps it, where it is the window's summary.
            health?.takeIf { !it.collecting }?.emaMs?.let { latency ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.dashboard_node_health_line, latency),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** F3: one line of state explanation, occupied or not. */
private val CONTEXT_SLOT_HEIGHT = 28.dp

/** D1: the telemetry block's height, with or without numbers. */
private val TELEMETRY_HEIGHT = 96.dp

/** D4: the health chart never gets smaller than this. */
private val HEALTH_MIN_HEIGHT = 96.dp

/** The health header is a fixed 32 dp so the chart's share is predictable. */
private val HEALTH_HEADER_HEIGHT = 32.dp

/**
 * A condition that lasts, as opposed to a one-off confirmation.
 *
 * Only these get the slot; everything else is a Snackbar. Adding a new notice
 * forces this decision instead of defaulting to "show it inline".
 */
private fun DashboardNotice.isPersistent(): Boolean = when (this) {
    DashboardNotice.KillSwitchBlocking, DashboardNotice.KillSwitchFailed -> true
    else -> false
}

private val RATE_UNITS = listOf("B/s", "KB/s", "MB/s", "GB/s")
private val BYTE_UNITS = listOf("B", "KB", "MB", "GB")

/** One kibibyte, the step between every pair of units above. */
private const val SCALE = 1024.0

internal fun formatRate(bytesPerSecond: Long): String =
    formatRateParts(bytesPerSecond).let { "${it.value} ${it.unit}" }

internal fun formatBytes(bytes: Long): String =
    formatScaled(bytes.toDouble(), BYTE_UNITS)

/**
 * [value] in the largest unit that keeps it readable.
 *
 * The unit is chosen from the **rounded** value, not the raw one, and that is
 * the whole point: rounding inside a unit is how "1023.6 KB" is shown as
 * "1023 KB/s" when it is really 1.0 MB/s, and §6.2 names that string as
 * forbidden. (A value that genuinely is 1023.0–1023.4 KB still reads
 * "1023 KB/s", because that is what it is.)
 *
 * Whole bytes and kilobytes — a fractional byte is noise — and one decimal
 * from megabytes up. `Locale.US` so the decimal point is a point: the default
 * locale renders "1,0 MB/s" in half of Europe, and the design is explicit about
 * the shape of the number.
 *
 * Zero reads "0 B/s", never "--": zero is a measurement.
 */
private fun formatScaled(value: Double, units: List<String>): String {
    var scaled = value
    for (index in units.indices) {
        val decimals = if (index <= 1) 0 else 1
        val rounded = roundTo(scaled, decimals)
        if (index == units.lastIndex || rounded < SCALE) {
            return if (decimals == 0) {
                "${rounded.toLong()} ${units[index]}"
            } else {
                String.format(Locale.US, "%.1f %s", rounded, units[index])
            }
        }
        scaled /= SCALE
    }
    // The loop returns on the last unit; this is here to satisfy the compiler.
    return "${value.toLong()} ${units.first()}"
}

private fun roundTo(value: Double, decimals: Int): Double {
    val factor = if (decimals == 0) 1.0 else 10.0
    return round(value * factor) / factor
}
