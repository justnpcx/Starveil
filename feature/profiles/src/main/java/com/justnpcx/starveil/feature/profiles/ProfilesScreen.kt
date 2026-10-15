package com.justnpcx.starveil.feature.profiles

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * Nodes — the real list plus the import entry points. A node arrives by
 * scanning a QR code, pasting from the clipboard, or typing a share link /
 * subscription URL; all three funnel through the same importer.
 */
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel = hiltViewModel(),
    /**
     * Measures the selected node's latency. Supplied by :app, which owns the
     * kernel; it reports back a user-facing message so a failed measurement is
     * never silent. The default keeps previews and tests kernel-free.
     */
    onTestLatency: ((String) -> Unit) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showImport by remember { mutableStateOf(false) }
    // Deleting is destructive, so the row's button only stages the node; the
    // confirmation below is what actually removes it.
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            viewModel.postMessage(context.getString(R.string.profiles_import_scan_cancelled))
        } else {
            showImport = false
            viewModel.import(contents)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            state.message?.let { message ->
                MessageBanner(message = message, onDismiss = viewModel::dismissMessage)
            }
            if (state.hasProfiles) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = StarveilSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onTestLatency { msg -> viewModel.postMessage(msg) } }) {
                        Text(stringResource(R.string.profiles_test_latency))
                    }
                }
            }
            if (state.groups.isNotEmpty()) {
                // Groups come from subscription imports; a node imported from a
                // single share link has none, so it only shows under "All".
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = StarveilSpacing.lg, vertical = StarveilSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
                ) {
                    GroupChip(
                        label = stringResource(R.string.profiles_group_all),
                        selected = state.groupFilter == null,
                        onClick = { viewModel.filterByGroup(null) },
                    )
                    state.groups.forEach { group ->
                        GroupChip(
                            label = group.name,
                            selected = state.groupFilter == group.id,
                            onClick = { viewModel.filterByGroup(group.id) },
                        )
                    }
                }
            }
            when {
                !state.hasProfiles -> EmptyState()

                // Nodes exist but the filter hides them: say so, rather than
                // reusing "No nodes yet", which would be a lie.
                state.hasNoMatches -> NoMatchesState()

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(StarveilSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
                ) {
                    items(state.profiles, key = { it.id }) { profile ->
                        NodeRow(
                            profile = profile,
                            selected = profile.id == state.selectedId,
                            onClick = { viewModel.select(profile.id) },
                            onDelete = { pendingDelete = profile },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showImport = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(StarveilSpacing.lg),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.profiles_import_add))
        }
    }

    if (showImport) {
        ImportDialog(
            importing = state.importing,
            onDismiss = { showImport = false },
            onImport = { text ->
                showImport = false
                viewModel.import(text)
            },
            onScan = {
                scanLauncher.launch(
                    ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt(context.getString(R.string.profiles_scan_prompt))
                        setBeepEnabled(false)
                        setOrientationLocked(false)
                    },
                )
            },
        )
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.profiles_delete_title)) },
            text = { Text(stringResource(R.string.profiles_delete_body, profile.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(profile.id)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.profiles_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.profiles_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun ImportDialog(
    importing: Boolean,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onScan: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }
    val clipboardEmptyHint = stringResource(R.string.profiles_import_clipboard_empty)

    // The usual flow is: copy a link in another app, then open this dialog. If
    // the clipboard already holds something importable, start from it.
    LaunchedEffect(Unit) {
        readClipboardText(context)?.takeIf(::looksImportable)?.let { text = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        hint = null
                    },
                    singleLine = false,
                    minLines = 3,
                    label = { Text(stringResource(R.string.profiles_import_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                hint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                    TextButton(
                        onClick = {
                            val clip = readClipboardText(context)
                            if (clip == null) hint = clipboardEmptyHint else {
                                text = clip
                                hint = null
                            }
                        },
                    ) {
                        Text(stringResource(R.string.profiles_import_paste))
                    }
                    TextButton(onClick = onScan) {
                        Text(stringResource(R.string.profiles_import_scan))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(text) },
                enabled = !importing && text.isNotBlank(),
            ) {
                Text(
                    stringResource(
                        if (importing) R.string.profiles_import_working else R.string.profiles_import_action,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_import_cancel)) }
        },
    )
}

/** Reads the primary clip as text; null when there is nothing to read. */
private fun readClipboardText(context: Context): String? {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Cheap "could this be a node?" check, used only to decide whether to prefill
 * from the clipboard. Deliberately permissive: anything with a scheme.
 */
private fun looksImportable(text: String): Boolean =
    text.contains("://") || text.startsWith("http", ignoreCase = true)

@Composable
private fun MessageBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = StarveilSpacing.lg, vertical = StarveilSpacing.sm),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = StarveilSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_message_dismiss)) }
        }
    }
}

@Composable
private fun NodeRow(
    profile: Profile,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = StarveilSpacing.lg, top = StarveilSpacing.sm, bottom = StarveilSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${profile.type.name.lowercase()} · ${profile.server}:${profile.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (profile.latencyMs >= 0) {
                    stringResource(R.string.profiles_latency_ms, profile.latencyMs)
                } else {
                    stringResource(R.string.profiles_latency_untested)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Its own button rather than a swipe: a node carries credentials
            // and re-importing one is not always possible, so the gesture has
            // to be deliberate and visible.
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.profiles_delete, profile.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A group filter chip (design system §6.4): capsule, raised surface when idle,
 * brand glow when selected.
 */
@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = StarveilColors.Raised,
            labelColor = StarveilColors.TextDim,
            selectedContainerColor = StarveilColors.GlowPrimary.copy(alpha = 0.2f),
            selectedLabelColor = StarveilColors.GlowPrimary,
        ),
    )
}

@Composable
private fun NoMatchesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(StarveilSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.profiles_no_matches_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.profiles_no_matches_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(StarveilSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        Text(text = stringResource(R.string.profiles_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.profiles_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
