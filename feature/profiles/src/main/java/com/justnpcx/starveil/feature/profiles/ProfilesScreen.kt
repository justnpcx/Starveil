package com.justnpcx.starveil.feature.profiles

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.ui.components.AppearOnFirstShow
import com.justnpcx.starveil.core.ui.components.rememberAppearance
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
    // Group management sits behind a long-press on a chip, because a tap has to
    // stay the filter. Three stages, like the node delete: menu, then the
    // rename or the confirmation.
    var groupMenu by remember { mutableStateOf<ProxyGroup?>(null) }
    var groupToRename by remember { mutableStateOf<ProxyGroup?>(null) }
    var groupToDelete by remember { mutableStateOf<ProxyGroup?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }
    // The node whose group is being changed.
    var movingProfile by remember { mutableStateOf<Profile?>(null) }
    // §5.2. Hoisted out of the LazyColumn so a row scrolled away and back does
    // not replay its entrance.
    val appeared = rememberAppearance()

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
            // Always shown, even with no groups: creating the first one has to
            // have somewhere to live.
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
                        onLongClick = { groupMenu = group },
                    )
                }
                GroupChip(
                    label = stringResource(R.string.profiles_group_new),
                    selected = false,
                    onClick = { creatingGroup = true },
                )
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
                    itemsIndexed(state.profiles, key = { _, profile -> profile.id }) { index, profile ->
                        // §5.2: rows settle in top-down instead of snapping in
                        // as a block. `appeared` lives out here so a row that
                        // scrolls away and back does not replay its entrance.
                        AppearOnFirstShow(key = profile.id, index = index, seen = appeared) {
                            NodeRow(
                                profile = profile,
                                selected = profile.id == state.selectedId,
                                onClick = { viewModel.select(profile.id) },
                                onLongClick = { movingProfile = profile },
                                onDelete = { pendingDelete = profile },
                            )
                        }
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

    groupMenu?.let { group ->
        AlertDialog(
            onDismissRequest = { groupMenu = null },
            title = { Text(group.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                    Text(stringResource(R.string.profiles_group_menu_body, group.profileIds.size))
                    TextButton(
                        onClick = {
                            groupMenu = null
                            groupToRename = group
                        },
                    ) {
                        Text(stringResource(R.string.profiles_group_rename))
                    }
                    TextButton(
                        onClick = {
                            groupMenu = null
                            groupToDelete = group
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.profiles_group_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { groupMenu = null }) {
                    Text(stringResource(R.string.profiles_group_cancel))
                }
            },
        )
    }

    groupToRename?.let { group ->
        GroupNameDialog(
            title = stringResource(R.string.profiles_group_rename_title),
            initial = group.name,
            onDismiss = { groupToRename = null },
            onConfirm = { name ->
                viewModel.renameGroup(group, name)
                groupToRename = null
            },
        )
    }

    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text(stringResource(R.string.profiles_group_delete_title)) },
            text = {
                Text(
                    // Say the node count: deleting a subscription group takes
                    // its nodes with it, and that is not obvious.
                    if (group.profileIds.isEmpty()) {
                        stringResource(R.string.profiles_group_delete_body_empty, group.name)
                    } else {
                        stringResource(
                            R.string.profiles_group_delete_body,
                            group.name,
                            group.profileIds.size,
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGroup(group)
                        groupToDelete = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.profiles_group_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text(stringResource(R.string.profiles_group_cancel))
                }
            },
        )
    }

    if (creatingGroup) {
        GroupNameDialog(
            title = stringResource(R.string.profiles_group_new_title),
            initial = "",
            onDismiss = { creatingGroup = false },
            onConfirm = { name ->
                viewModel.createGroup(name)
                creatingGroup = false
            },
        )
    }

    movingProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { movingProfile = null },
            title = { Text(stringResource(R.string.profiles_group_move_title, profile.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                    // "No group" first: taking a node back out is as common as
                    // putting it in, and a node from a share link starts there.
                    TextButton(
                        onClick = {
                            viewModel.moveToGroup(profile, null)
                            movingProfile = null
                        },
                    ) {
                        Text(stringResource(R.string.profiles_group_none))
                    }
                    state.groups.forEach { group ->
                        TextButton(
                            onClick = {
                                viewModel.moveToGroup(profile, group.id)
                                movingProfile = null
                            },
                            enabled = group.id != profile.groupId,
                        ) {
                            Text(group.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { movingProfile = null }) {
                    Text(stringResource(R.string.profiles_group_cancel))
                }
            },
        )
    }
}

/** Name entry, shared by "new group" and "rename". */
@Composable
private fun GroupNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.profiles_group_name_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.profiles_group_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profiles_group_cancel))
            }
        },
    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeRow(
    profile: Profile,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        // Long-press moves the node between groups; a tap stays the selection.
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
 *
 * Long-press opens group management. A tap has to stay the filter, so the
 * destructive actions cannot live there.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) {
            StarveilColors.GlowPrimary.copy(alpha = GROUP_SELECTED_ALPHA)
        } else {
            StarveilColors.Raised
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) StarveilColors.GlowPrimary else StarveilColors.TextDim,
            modifier = Modifier.padding(
                horizontal = StarveilSpacing.md,
                vertical = StarveilSpacing.sm,
            ),
        )
    }
}

/** Selected chip fill: the spec's "GlowPrimary 20%". */
private const val GROUP_SELECTED_ALPHA = 0.2f

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
