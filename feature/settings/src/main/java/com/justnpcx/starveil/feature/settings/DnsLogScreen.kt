package com.justnpcx.starveil.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.justnpcx.starveil.core.data.geo.DnsEntry
import com.justnpcx.starveil.core.data.geo.DnsOutcome
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.components.StarveilDivider
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType
import com.justnpcx.starveil.core.ui.util.writeClipboard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * The per-query DNS log.
 *
 * The first thing on the page is what this is not: the ring is fed by the
 * GeoIP DoH probe that resolves node hostnames, not by the tunnel's DNS. A
 * small sample of probe timings says nothing about how the tunnel resolves,
 * and the note exists so the numbers cannot be read as if it did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsLogScreen(
    onBack: () -> Unit,
    viewModel: DnsLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var previewing by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dns_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            StarveilIcons.Back,
                            contentDescription = stringResource(R.string.dns_log_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        enabled = state.snapshot.samples > 0,
                        onClick = { previewing = true },
                    ) {
                        Text(
                            stringResource(
                                if (copied) R.string.dns_log_copied else R.string.dns_log_export,
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.dns_log_scope),
                style = StarveilType.label,
                color = StarveilColors.warn,
                modifier = Modifier.padding(
                    start = StarveilSpacing.pageMargin,
                    end = StarveilSpacing.pageMargin,
                    top = StarveilSpacing.md,
                    bottom = StarveilSpacing.md,
                ),
            )

            StarveilCard(modifier = Modifier.padding(horizontal = StarveilSpacing.pageMargin)) {
                Column(modifier = Modifier.padding(StarveilSpacing.lg)) {
                    if (state.snapshot.samples == 0) {
                        Text(
                            text = stringResource(R.string.dns_log_empty),
                            style = StarveilType.label,
                            color = StarveilColors.ink2,
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.dns_log_stats,
                                state.snapshot.samples,
                                state.snapshot.p50Ms,
                                state.snapshot.p95Ms,
                                state.snapshot.fallbackPct,
                                state.snapshot.cacheHitPct,
                            ),
                            style = StarveilType.log,
                            color = StarveilColors.ink,
                        )
                        Spacer(modifier = Modifier.height(StarveilSpacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.lg)) {
                            TextButton(onClick = viewModel::refresh) {
                                Text(stringResource(R.string.dns_log_refresh))
                            }
                            TextButton(onClick = viewModel::clear) {
                                Text(stringResource(R.string.dns_log_clear))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(StarveilSpacing.md))

            if (state.entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.dns_log_empty_body),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                    modifier = Modifier.padding(horizontal = StarveilSpacing.pageMargin),
                )
            } else {
                StarveilCard(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = StarveilSpacing.pageMargin),
                ) {
                    LazyColumn(contentPadding = PaddingValues(bottom = StarveilSpacing.md)) {
                        items(state.entries, key = { "${it.atMs}-${it.host}-${it.durationMs}" }) { entry ->
                            EntryRow(entry)
                            if (entry != state.entries.last()) {
                                StarveilDivider(startInset = StarveilSpacing.lg)
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.dns_log_footer),
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

    if (previewing) {
        AlertDialog(
            onDismissRequest = { previewing = false },
            title = { Text(stringResource(R.string.dns_log_preview_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = state.exportText,
                        style = StarveilType.log,
                        color = StarveilColors.ink2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        writeClipboard(context, state.exportText, "Starveil DNS log")
                        previewing = false
                        copied = true
                    },
                ) {
                    Text(stringResource(R.string.dns_log_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { previewing = false }) {
                    Text(stringResource(R.string.dns_log_cancel))
                }
            },
        )
    }
}

@Composable
private fun EntryRow(entry: DnsEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = StarveilSpacing.lg, vertical = StarveilSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = TIME_FORMAT.format(
                    Instant.ofEpochMilli(entry.atMs).atZone(ZoneId.systemDefault()),
                ),
                style = StarveilType.tech,
                color = StarveilColors.ink3,
            )
            Spacer(modifier = Modifier.width(StarveilSpacing.sm))
            Text(
                text = entry.host.ifBlank { stringResource(R.string.dns_log_literal) },
                style = StarveilType.body,
                color = StarveilColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(StarveilSpacing.sm))
            Text(
                text = stringResource(R.string.dns_log_duration, entry.durationMs),
                style = StarveilType.numInline,
                color = StarveilColors.ink,
            )
        }
        Spacer(modifier = Modifier.height(StarveilSpacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.resolver.ifBlank { stringResource(R.string.dns_log_resolver_unknown) },
                style = StarveilType.tech,
                color = StarveilColors.ink3,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = outcomeLabel(entry.outcome),
                style = StarveilType.tech,
                color = when (entry.outcome) {
                    DnsOutcome.DOH, DnsOutcome.CACHE -> StarveilColors.ok
                    DnsOutcome.LITERAL -> StarveilColors.ink3
                    DnsOutcome.FALLBACK -> StarveilColors.warn
                    DnsOutcome.FAILED -> StarveilColors.errText
                },
            )
        }
    }
}

@Composable
private fun outcomeLabel(outcome: DnsOutcome): String = stringResource(
    when (outcome) {
        DnsOutcome.DOH -> R.string.dns_log_outcome_doh
        DnsOutcome.FALLBACK -> R.string.dns_log_outcome_fallback
        DnsOutcome.CACHE -> R.string.dns_log_outcome_cache
        DnsOutcome.LITERAL -> R.string.dns_log_outcome_literal
        DnsOutcome.FAILED -> R.string.dns_log_outcome_failed
    },
)
