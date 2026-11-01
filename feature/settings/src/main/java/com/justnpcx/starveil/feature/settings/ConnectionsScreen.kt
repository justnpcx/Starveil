package com.justnpcx.starveil.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.model.ConnectionDetail
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.components.StarveilChip
import com.justnpcx.starveil.core.ui.components.StarveilChipRow
import com.justnpcx.starveil.core.ui.components.StarveilDivider
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The live connection table.
 *
 * One row per connection the kernel is carrying, plus the recent ones it
 * finished. The columns are the kernel's own facts — app, destination, rule,
 * outbound, bytes, duration — and the latency column is the exit node's
 * measured latency, because the kernel's connection record has no round-trip
 * time of its own. The note under the table says so rather than letting a
 * number imply more than it knows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Package labels are resolved once per distinct package, not per row: a
    // busy tunnel repeats the same five packages hundreds of times.
    val appLabels = remember(state.apps) {
        val manager = context.packageManager
        state.apps.associateWith { pkg ->
            runCatching {
                val info = manager.getApplicationInfo(pkg, 0)
                manager.getApplicationLabel(info).toString()
            }.getOrDefault(pkg)
        }
    }

    // No bar of its own: the shell draws the title and the arrow back.
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.connections_intro),
                style = StarveilType.label,
                color = StarveilColors.ink2,
                modifier = Modifier.padding(
                    start = StarveilSpacing.pageMargin,
                    end = StarveilSpacing.pageMargin,
                    bottom = StarveilSpacing.md,
                ),
            )

            // The same row component the node list uses: one geometry, one set
            // of edge fades, one place to change it. The row used to be a plain
            // scrolling Row padded from the inside, which put its first chip on
            // the page margin but let the last one be cut by the screen edge.
            StarveilChipRow {
                StarveilChip(
                    label = stringResource(R.string.connections_filter_active),
                    selected = state.filters.onlyActive,
                    onClick = { viewModel.setOnlyActive(true) },
                )
                StarveilChip(
                    label = stringResource(R.string.connections_filter_all),
                    selected = !state.filters.onlyActive,
                    onClick = { viewModel.setOnlyActive(false) },
                )
                FilterMenu(
                    label = stringResource(R.string.connections_filter_app),
                    value = state.filters.app,
                    options = state.apps,
                    display = { appLabels[it] ?: it },
                    onSelect = viewModel::setApp,
                )
                FilterMenu(
                    label = stringResource(R.string.connections_filter_node),
                    value = state.filters.node,
                    options = state.nodes,
                    display = { state.nodeNameByTag[it] ?: it },
                    onSelect = viewModel::setNode,
                )
                FilterMenu(
                    label = stringResource(R.string.connections_filter_rule),
                    value = state.filters.rule,
                    options = state.rules,
                    display = { if (it.isBlank()) stringResource(R.string.connections_rule_none) else it },
                    onSelect = viewModel::setRule,
                )
            }

            Spacer(modifier = Modifier.height(StarveilSpacing.md))

            when {
                state.total == 0 -> Empty(
                    title = stringResource(R.string.connections_empty_title),
                    body = stringResource(R.string.connections_empty_body),
                )

                state.rows.isEmpty() -> Empty(
                    title = stringResource(R.string.connections_no_matches_title),
                    body = stringResource(R.string.connections_no_matches_body),
                )

                else -> StarveilCard(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = StarveilSpacing.pageMargin),
                ) {
                    LazyColumn(contentPadding = PaddingValues(bottom = StarveilSpacing.md)) {
                        items(state.rows, key = { it.id }) { row ->
                            ConnectionRow(
                                row = row,
                                nowMs = state.nowMs,
                                appLabel = appLabels[row.appPackage] ?: row.appPackage,
                                nodeLabel = state.nodeNameByTag[row.outbound] ?: row.outbound,
                                latencyMs = state.latencyByTag[row.outbound],
                            )
                            if (row != state.rows.last()) {
                                StarveilDivider(startInset = StarveilSpacing.lg)
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.connections_latency_note),
                style = StarveilType.tech,
                color = StarveilColors.ink3,
                modifier = Modifier.padding(
                    start = StarveilSpacing.pageMargin,
                    end = StarveilSpacing.pageMargin,
                    top = StarveilSpacing.md,
                    bottom = StarveilSpacing.xl,
                ),
            )
        }
    }
}

/**
 * One connection: when, who, where, which rule, which node, how much, how long.
 *
 * Three lines rather than a table of eight columns: a phone is 1080 px wide and
 * a table that needs a horizontal scroll hides exactly the column someone
 * opened the screen to read.
 */
@Composable
private fun ConnectionRow(
    row: ConnectionDetail,
    nowMs: Long,
    appLabel: String,
    nodeLabel: String,
    latencyMs: Long?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = StarveilSpacing.lg,
                vertical = StarveilSpacing.md,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = TIME_FORMAT.format(
                    Instant.ofEpochMilli(row.startedAtMs).atZone(ZoneId.systemDefault()),
                ),
                style = StarveilType.tech,
                color = StarveilColors.ink3,
            )
            Spacer(modifier = Modifier.width(StarveilSpacing.sm))
            Text(
                text = appLabel.ifBlank { stringResource(R.string.connections_app_unknown) },
                style = StarveilType.label,
                color = StarveilColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(StarveilSpacing.sm))
            Text(
                text = stringResource(
                    if (row.open) R.string.connections_status_open else R.string.connections_status_closed,
                ),
                style = StarveilType.tech,
                color = if (row.open) StarveilColors.ok else StarveilColors.ink3,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(StarveilSpacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.label,
                style = StarveilType.body,
                color = StarveilColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(StarveilSpacing.sm))
            Text(
                text = nodeLabel,
                style = StarveilType.tech,
                color = StarveilColors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.7f, fill = false),
            )
        }
        Spacer(modifier = Modifier.height(StarveilSpacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.rule.ifBlank { stringResource(R.string.connections_rule_none) },
                style = StarveilType.tech,
                color = StarveilColors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(StarveilSpacing.sm))
            Text(
                text = stringResource(
                    R.string.connections_metrics,
                    formatBytes(row.totalBytes),
                    formatDuration(row.durationMs(nowMs)),
                ),
                style = StarveilType.tech,
                color = StarveilColors.ink2,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(StarveilSpacing.sm))
            Text(
                text = latencyMs?.let { stringResource(R.string.connections_latency, it) }
                    ?: stringResource(R.string.connections_latency_unknown),
                style = StarveilType.tech,
                color = StarveilColors.ink3,
                maxLines = 1,
            )
        }
    }
}

/**
 * A filter as a chip that opens its values.
 *
 * A chip rather than a dropdown row of its own: three dropdown rows would take
 * a third of the screen before the first connection, and the values are only
 * interesting once there is something to narrow.
 */
@Composable
private fun FilterMenu(
    label: String,
    value: String?,
    options: List<String>,
    display: @Composable (String) -> String,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        StarveilChip(
            label = if (value == null) label else display(value),
            selected = value != null,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.connections_filter_any)) },
                onClick = {
                    open = false
                    onSelect(null)
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun Empty(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = StarveilSpacing.pageMargin, vertical = StarveilSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = StarveilType.headline, color = StarveilColors.ink)
        Spacer(modifier = Modifier.height(StarveilSpacing.sm))
        Text(
            text = body,
            style = StarveilType.label,
            color = StarveilColors.ink2,
        )
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private val BYTE_UNITS = listOf("B", "KB", "MB", "GB")

private fun formatBytes(bytes: Long): String {
    // The counters are sums of deltas the kernel reports; a negative total is
    // not a size, and "‑1 B" would read as a corrupted row rather than as the
    // missing data it is.
    if (bytes <= 0L) return "0 B"
    var value = bytes.toDouble()
    for ((index, unit) in BYTE_UNITS.withIndex()) {
        val last = index == BYTE_UNITS.lastIndex
        if (value < 1024 || last) {
            return if (index == 0) {
                "${value.toLong()} $unit"
            } else {
                String.format(Locale.US, "%.1f %s", value, unit)
            }
        }
        value /= 1024
    }
    return "$bytes B"
}

/**
 * A duration in the largest unit that still says something.
 *
 * Seconds, then minutes and seconds, then hours and minutes, then days. A null
 * duration is a dash: the row exists, the start does not.
 */
private fun formatDuration(ms: Long?): String = when {
    ms == null -> "—"
    ms < 1_000 -> "${ms}ms"
    ms < 60_000 -> "${ms / 1_000}s"
    ms < 3_600_000 -> "${ms / 60_000}m ${(ms % 60_000) / 1_000}s"
    ms < 86_400_000 -> "${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m"
    else -> "${ms / 86_400_000}d ${(ms % 86_400_000) / 3_600_000}h"
}
