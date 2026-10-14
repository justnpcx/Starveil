package com.justnpcx.starveil.feature.logs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.model.AppLogLevel
import com.justnpcx.starveil.core.model.AppLogLine
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Logs — the kernel's log lines, newest first.
 *
 * This exists because on some ROMs (the vivo test device among them) `adb
 * logcat` is unreliable or filtered, which leaves the app unable to say why a
 * connection failed. The lines arrive already redacted.
 *
 * Search and redacted export are still to come; the level filter is the part
 * that matters for diagnosis.
 */
@Composable
fun LogsScreen(
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.logs_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(
                start = StarveilSpacing.lg,
                top = StarveilSpacing.lg,
                end = StarveilSpacing.lg,
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = StarveilSpacing.lg, vertical = StarveilSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
        ) {
            FilterChip(
                selected = state.levelFilter == null,
                onClick = { state.levelFilter?.let(viewModel::toggleLevel) },
                label = { Text(stringResource(R.string.logs_filter_all)) },
            )
            AppLogLevel.entries.forEach { level ->
                FilterChip(
                    selected = state.levelFilter == level,
                    onClick = { viewModel.toggleLevel(level) },
                    label = { Text(level.name) },
                )
            }
        }

        if (state.isEmpty) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(StarveilSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.logs_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.logs_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = StarveilSpacing.lg,
                    vertical = StarveilSpacing.sm,
                ),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.xs),
            ) {
                items(state.lines, key = { it.timestampMs to it.message.hashCode() }) { line ->
                    LogRow(line)
                }
            }
        }
    }
}

@Composable
private fun LogRow(line: AppLogLine) {
    Row(horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
        Text(
            text = TIME_FORMAT.format(Instant.ofEpochMilli(line.timestampMs).atZone(ZoneId.systemDefault())),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = line.level.name.take(1),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = line.level.colour(),
        )
        Text(
            text = line.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            // The reason a connection failed is at the END of the line, so a
            // tight cap hides exactly the part being looked for.
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Warn and error carry the theme's error colour; the rest stay neutral. */
@Composable
private fun AppLogLevel.colour(): Color = when (this) {
    AppLogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    AppLogLevel.ERROR -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
