package com.justnpcx.starveil.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.ui.components.ConnectionCore
import com.justnpcx.starveil.core.ui.components.ConnectionState
import com.justnpcx.starveil.core.ui.components.TrafficSparkline
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
            .padding(StarveilSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(modifier = Modifier.height(StarveilSpacing.xl))
        ConnectionCore(
            state = state.connectionState,
            onClick = { requestVpnConsent { viewModel.onCoreClick() } },
            // The core is the screen's primary control, so its description
            // carries both the state and what tapping does (design system §6.1).
            contentDescription = stringResource(
                when (state.connectionState) {
                    ConnectionState.DISCONNECTED -> R.string.dashboard_core_disconnected
                    ConnectionState.CONNECTING -> R.string.dashboard_core_connecting
                    ConnectionState.CONNECTED -> R.string.dashboard_core_connected
                    ConnectionState.BLOCKED -> R.string.dashboard_core_blocked
                },
            ),
        )
        Spacer(modifier = Modifier.height(StarveilSpacing.sm))
        Text(
            text = state.connectionState.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = state.selectedNodeName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.errorMessage?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(StarveilSpacing.xl))
        SpeedRow(stats = state.stats)
        Spacer(modifier = Modifier.height(StarveilSpacing.lg))
        TrafficSparkline(
            downlinkSamples = state.history.map { it.downlink },
            uplinkSamples = state.history.map { it.uplink },
        )
        Spacer(modifier = Modifier.height(StarveilSpacing.lg))
        CurrentNodeCard(name = state.selectedNodeName)
    }
}

@Composable
private fun SpeedRow(stats: TrafficStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SpeedCell(label = "↓ DOWN", rate = stats.downlinkRate, total = stats.downlinkBytes)
        SpeedCell(label = "↑ UP", rate = stats.uplinkRate, total = stats.uplinkBytes)
    }
}

@Composable
private fun SpeedCell(label: String, rate: Long, total: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = formatRate(rate), style = MaterialTheme.typography.displayMedium)
        Text(
            text = "total ${formatBytes(total)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The node the next connect will use; the in-place selector is M3 D5. */
@Composable
private fun CurrentNodeCard(name: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(StarveilSpacing.lg)) {
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
    }
}

private fun formatRate(bytesPerSecond: Long): String = when {
    bytesPerSecond < 1_024 -> "$bytesPerSecond B/s"
    bytesPerSecond < 1_024 * 1_024 -> "${bytesPerSecond / 1_024} KB/s"
    else -> String.format("%.1f MB/s", bytesPerSecond / 1_048_576.0)
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024 * 1_024 -> "${bytes / 1_024} KB"
    bytes < 1_024 * 1_024 * 1_024 -> String.format("%.1f MB", bytes / 1_048_576.0)
    else -> String.format("%.2f GB", bytes / 1_073_741_824.0)
}
