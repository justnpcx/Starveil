package com.justnpcx.starveil.feature.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/** How long the export button says "Copied" before reverting. */
private const val COPIED_FEEDBACK_MS = 2_000L

/**
 * Logs — the kernel's log lines, newest first, with a level filter, a text
 * search and a redacted export.
 *
 * This exists because on some ROMs (the vivo test device among them) `adb
 * logcat` is unreliable or filtered, which leaves the app unable to say why a
 * connection failed. The lines arrive already redacted, so exporting them
 * cannot leak credentials.
 */
@Composable
fun LogsScreen(
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The button itself carries the confirmation, so a copy needs no extra
    // banner. The counter lets a second tap restart the timer.
    var copySignal by remember { mutableIntStateOf(0) }
    var showCopied by remember { mutableStateOf(false) }
    LaunchedEffect(copySignal) {
        if (copySignal == 0) return@LaunchedEffect
        showCopied = true
        delay(COPIED_FEEDBACK_MS)
        showCopied = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = StarveilSpacing.lg, top = StarveilSpacing.lg, end = StarveilSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.logs_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    copyToClipboard(context, exportText(state.lines))
                    copySignal++
                },
                enabled = state.lines.isNotEmpty(),
            ) {
                Text(stringResource(if (showCopied) R.string.logs_copied else R.string.logs_copy))
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            singleLine = true,
            label = { Text(stringResource(R.string.logs_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.logs_search_clear),
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = StarveilSpacing.lg, vertical = StarveilSpacing.sm),
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

        when {
            state.isEmpty -> EmptyState(
                title = stringResource(R.string.logs_empty_title),
                body = stringResource(R.string.logs_empty_body),
            )

            // Lines exist but the filter hides them — say so, rather than
            // reusing the "nothing yet" copy, which would be a lie.
            state.hasNoMatches -> EmptyState(
                title = stringResource(R.string.logs_no_matches_title),
                body = stringResource(R.string.logs_no_matches_body),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = StarveilSpacing.lg,
                    vertical = StarveilSpacing.sm,
                ),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.xs),
            ) {
                // Keyed on the line's own id. A key built from the timestamp and
                // the text is not unique — sing-box can log the same line twice
                // in one millisecond — and a duplicate key throws.
                items(state.lines, key = { it.seq }) { line ->
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

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(StarveilSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The lines as plain text, oldest first — what a log file looks like. The
 * caller passes the currently visible lines (already filtered), so what is
 * copied is what is on screen.
 *
 * Redaction happens before a line reaches the stream, so this cannot export a
 * credential even if the kernel printed one.
 */
private fun exportText(lines: List<AppLogLine>): String =
    lines.asReversed().joinToString("\n") { line ->
        val time = TIME_FORMAT.format(Instant.ofEpochMilli(line.timestampMs).atZone(ZoneId.systemDefault()))
        "$time ${line.level.name.take(1)} ${line.message}"
    }

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    manager.setPrimaryClip(ClipData.newPlainText("Starveil logs", text))
}

/** Warn and error carry the theme's error colour; the rest stay neutral. */
@Composable
private fun AppLogLevel.colour(): Color = when (this) {
    AppLogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    AppLogLevel.ERROR -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
