package com.justnpcx.starveil.feature.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.model.AppLogLevel
import com.justnpcx.starveil.core.model.AppLogLine
import com.justnpcx.starveil.core.ui.components.EmptyState
import com.justnpcx.starveil.core.ui.components.StarveilChip
import com.justnpcx.starveil.core.ui.components.StarveilChipRow
import com.justnpcx.starveil.core.ui.components.StarveilPageHeader
import com.justnpcx.starveil.core.ui.components.StarveilSearchField
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType
import com.justnpcx.starveil.core.ui.util.writeClipboard
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
 * The lines are bare on the page: no card, no row rules. A log is a column of
 * text, and boxing it would put an edge between the reader and the only thing
 * on the screen.
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
        StarveilPageHeader(title = stringResource(R.string.logs_title)) {
            Text(
                text = stringResource(if (showCopied) R.string.logs_copied else R.string.logs_copy),
                style = StarveilType.label,
                color = if (state.lines.isEmpty()) StarveilColors.ink3 else StarveilColors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(StarveilSpacing.xs))
                    .clickable(
                        enabled = state.lines.isNotEmpty(),
                        role = Role.Button,
                    ) {
                        writeClipboard(context, exportText(state.lines), "Starveil logs")
                        copySignal++
                    }
                    .padding(horizontal = StarveilSpacing.md, vertical = StarveilSpacing.sm),
            )
        }

        StarveilSearchField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = stringResource(R.string.logs_search_hint),
            modifier = Modifier.padding(
                start = StarveilSpacing.pageMargin,
                end = StarveilSpacing.pageMargin,
                bottom = StarveilSpacing.sm,
            ),
        )

        StarveilChipRow {
            StarveilChip(
                label = stringResource(R.string.logs_filter_all),
                selected = state.levelFilter == null,
                onClick = { state.levelFilter?.let(viewModel::toggleLevel) },
            )
            AppLogLevel.entries.forEach { level ->
                StarveilChip(
                    label = level.name,
                    selected = state.levelFilter == level,
                    onClick = { viewModel.toggleLevel(level) },
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
                // No row rules and no spacing between lines: the line height is
                // the rhythm, and a divider every 19dp would be a stripe.
                contentPadding = PaddingValues(
                    start = StarveilSpacing.pageMargin,
                    end = StarveilSpacing.pageMargin,
                    top = StarveilSpacing.sm,
                    bottom = StarveilSpacing.xxl,
                ),
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
            style = StarveilType.log,
            color = StarveilColors.ink3,
        )
        Text(
            // The level is a letter, not a badge: a coloured block behind a
            // one-character label is a stripe of colour down the page.
            text = line.level.name.take(1),
            style = StarveilType.log,
            color = line.level.colour(),
        )
        Spacer(modifier = Modifier.width(StarveilSpacing.xs))
        Text(
            text = line.message,
            style = StarveilType.log,
            color = StarveilColors.ink2,
            // The reason a connection failed is at the END of the line, so a
            // tight cap hides exactly the part being looked for.
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
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

/** The level as a word colour: DEBUG dim, INFO secondary, WARN amber, ERROR red. */
@Composable
private fun AppLogLevel.colour(): Color = when (this) {
    AppLogLevel.DEBUG -> StarveilColors.ink3
    AppLogLevel.INFO -> StarveilColors.ink2
    AppLogLevel.WARN -> StarveilColors.warn
    AppLogLevel.ERROR -> StarveilColors.errText
}
