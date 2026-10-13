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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.ui.components.ConnectionCore

/**
 * Dashboard — the cockpit. Tap the core to connect/disconnect.
 * Node selector lives HERE (not on a separate page) by design.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        ConnectionCore(
            state = state.connectionState,
            onClick = viewModel::onCoreClick,
        )
        Spacer(modifier = Modifier.height(8.dp))
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
        Spacer(modifier = Modifier.height(24.dp))
        SpeedRow(stats = state.stats)
        Spacer(modifier = Modifier.height(16.dp))
        NodeSelectorPlaceholder()
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

/** TODO (Phase 3): node selector with health sparklines + smart routing toggle. */
@Composable
private fun NodeSelectorPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Node selector", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "待实现：节点列表 + 健康曲线 + 自动选路开关（Phase 3）",
                style = MaterialTheme.typography.bodyMedium,
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
