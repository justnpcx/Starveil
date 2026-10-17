package com.justnpcx.starveil.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.model.AvailabilityBand
import com.justnpcx.starveil.core.model.NodeHealth
import com.justnpcx.starveil.core.model.RecommendationReason
import com.justnpcx.starveil.core.ui.components.ConnectionCore
import com.justnpcx.starveil.core.ui.components.ConnectionState
import com.justnpcx.starveil.core.ui.components.MessageBanner
import com.justnpcx.starveil.core.ui.components.NodeHealthChart
import com.justnpcx.starveil.core.ui.components.TrafficSparkline
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(StarveilSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(modifier = Modifier.height(StarveilSpacing.xl))
        ConnectionCore(
            state = state.coreState,
            onClick = { requestVpnConsent { viewModel.onCoreClick() } },
            // The core is the screen's primary control, so its description
            // carries both the state and what tapping does (design system §6.1).
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
        Spacer(modifier = Modifier.height(StarveilSpacing.sm))
        Text(
            text = stringResource(
                when (state.coreState) {
                    ConnectionState.DISCONNECTED -> R.string.dashboard_state_disconnected
                    ConnectionState.CONNECTING -> R.string.dashboard_state_connecting
                    ConnectionState.CONNECTED -> R.string.dashboard_state_connected
                    ConnectionState.BLOCKED -> R.string.dashboard_state_blocked
                    ConnectionState.WARNING -> R.string.dashboard_state_warning
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = state.selectedNodeName ?: stringResource(R.string.dashboard_no_node),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The same banner the Nodes screen uses (K3), but not dismissible: it
        // says why the core is blocked, and dismissing it would leave a blocked
        // core with no reason on screen (C5).
        state.notice?.let { MessageBanner(message = it.text()) }
        Spacer(modifier = Modifier.height(StarveilSpacing.xl))
        SpeedRow(stats = state.stats)
        Spacer(modifier = Modifier.height(StarveilSpacing.lg))
        TrafficSparkline(
            downlinkSamples = state.history.map { it.downlink },
            uplinkSamples = state.history.map { it.uplink },
            // The rates above are the current ones; the peak is the only thing
            // the shape says that they do not.
            contentDescription = stringResource(
                R.string.dashboard_traffic_chart_desc,
                state.history.size,
                formatRate(state.history.maxOfOrNull { it.downlink } ?: 0L),
            ),
        )
        Spacer(modifier = Modifier.height(StarveilSpacing.lg))
        CurrentNodeCard(
            name = state.selectedNodeName ?: stringResource(R.string.dashboard_no_node),
            onAutoSelect = viewModel::autoSelect,
        )
        // §7.2: the selected node's health over the window. Only for a node —
        // a group has no single curve — and the chart says "collecting" in
        // words until there is a shape worth drawing.
        state.health?.let { health ->
            Spacer(modifier = Modifier.height(StarveilSpacing.lg))
            NodeHealthSection(health)
        }
    }
}

/**
 * §7.2's health history: the curve, its availability bar and its loss dots,
 * under a line that says what the area is.
 */
@Composable
private fun NodeHealthSection(health: NodeHealth) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dashboard_health_title),
                style = MaterialTheme.typography.labelSmall,
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
                    style = MaterialTheme.typography.labelSmall,
                    color = when (health.band) {
                        AvailabilityBand.GOOD -> StarveilColors.Connected
                        AvailabilityBand.FAIR -> StarveilColors.Warning
                        AvailabilityBand.POOR -> StarveilColors.Blocked
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(StarveilSpacing.sm))
        val reachable = health.samples.filter { it.reachable }
        NodeHealthChart(
            health = health,
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
        )
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
    is RecommendationReason.Healthiest ->
        stringResource(R.string.dashboard_reason_healthiest, score)
    is RecommendationReason.TiedOnScore -> region
        ?.let { stringResource(R.string.dashboard_reason_tied_region, score, it.displayName) }
        ?: stringResource(R.string.dashboard_reason_tied, score)
    is RecommendationReason.AllCoolingDown ->
        stringResource(R.string.dashboard_reason_cooling_down, minutesLeft)
    RecommendationReason.NothingMeasured ->
        stringResource(R.string.dashboard_reason_unmeasured)
}

@Composable
private fun SpeedRow(stats: TrafficStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SpeedCell(
            label = stringResource(R.string.dashboard_speed_down),
            rate = stats.downlinkRate,
            total = stats.downlinkBytes,
        )
        SpeedCell(
            label = stringResource(R.string.dashboard_speed_up),
            rate = stats.uplinkRate,
            total = stats.uplinkBytes,
        )
    }
}

@Composable
private fun SpeedCell(label: String, rate: Long, total: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = formatRate(rate), style = MaterialTheme.typography.displayMedium)
        Text(
            text = stringResource(R.string.dashboard_speed_total, formatBytes(total)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The node the next connect will use, with the one-tap pick beside it. */
@Composable
private fun CurrentNodeCard(name: String, onAutoSelect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(StarveilSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dashboard_current_node),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAutoSelect) {
                Text(stringResource(R.string.dashboard_auto_select))
            }
        }
    }
}

private val RATE_UNITS = listOf("B/s", "KB/s", "MB/s", "GB/s")
private val BYTE_UNITS = listOf("B", "KB", "MB", "GB")

/** One kibibyte, the step between every pair of units above. */
private const val SCALE = 1024.0

internal fun formatRate(bytesPerSecond: Long): String =
    formatScaled(bytesPerSecond.toDouble(), RATE_UNITS)

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
