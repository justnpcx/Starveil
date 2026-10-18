package com.justnpcx.starveil.feature.profiles

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.justnpcx.starveil.core.model.NodeHealth
import com.justnpcx.starveil.core.model.NodeStatus
import com.justnpcx.starveil.core.ui.components.LatencySparkline
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import com.justnpcx.starveil.core.model.GroupMode
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.displayName
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.ui.components.AppearOnFirstShow
import com.justnpcx.starveil.core.ui.components.EmptyState
import com.justnpcx.starveil.core.ui.components.MessageBanner
import com.justnpcx.starveil.core.ui.components.rememberAppearance
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.util.readClipboardText
import com.justnpcx.starveil.core.ui.util.writeClipboard

/**
 * Nodes — the real list plus the import entry points. A node arrives by
 * scanning a QR code, pasting from the clipboard, or typing a share link /
 * subscription URL; all three funnel through the same importer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel = hiltViewModel(),
    /**
     * Measures the selected node's latency. Supplied by :app, which owns the
     * kernel; it reports back a user-facing message so a failed measurement is
     * never silent. The default keeps previews and tests kernel-free.
     */
    onTestLatency: ((String) -> Unit) -> Unit = {},
    /** Opens the editor for a brand-new node of the chosen protocol. */
    onNewNode: (ProfileType) -> Unit = {},
    /** Opens the editor for an existing node. */
    onEditNode: (ProfileType, Long) -> Unit = { _, _ -> },
    /** Measures one node by id, and reports what happened. */
    onTestNode: (Long, (String) -> Unit) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showImport by remember { mutableStateOf(false) }
    // Group management sits behind a long-press on a chip, because a tap has to
    // stay the filter. Three stages, like the node delete: menu, then the
    // rename or the confirmation.
    var groupMenu by remember { mutableStateOf<ProxyGroup?>(null) }
    var groupToRename by remember { mutableStateOf<ProxyGroup?>(null) }
    var groupToDelete by remember { mutableStateOf<ProxyGroup?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }
    // The node whose group is being changed.
    var movingProfile by remember { mutableStateOf<Profile?>(null) }
    // Long-press a node for its actions. A tap has to stay "select", because
    // that is what the dashboard connects to.
    var rowMenu by remember { mutableStateOf<Profile?>(null) }
    var pickingProtocol by remember { mutableStateOf(false) }
    // One add entry, four ways in. Two buttons that looked alike but did
    // different things was the original complaint.
    var showAddSheet by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    // §5.2. Hoisted out of the LazyColumn so a row scrolled away and back does
    // not replay its entrance.
    val appeared = rememberAppearance()
    // Resolved once per group list rather than per row: a subscription can hold
    // hundreds of nodes, and a linear scan per row would be hundreds of scans
    // per recomposition.
    val groupNames = remember(state.groups) { state.groups.associate { it.id to it.name } }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            viewModel.postMessage(context.getString(R.string.profiles_import_scan_cancelled))
        } else {
            showImport = false
            viewModel.import(contents)
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                AddSheetRow(stringResource(R.string.profiles_add_manual)) {
                    showAddSheet = false
                    pickingProtocol = true
                }
                AddSheetRow(stringResource(R.string.profiles_add_clipboard)) {
                    showAddSheet = false
                    val clip = readClipboardText(context)
                    if (clip.isNullOrBlank()) {
                        viewModel.postMessage(context.getString(R.string.profiles_import_clipboard_empty))
                    } else {
                        viewModel.import(clip)
                    }
                }
                AddSheetRow(stringResource(R.string.profiles_add_scan)) {
                    showAddSheet = false
                    scanLauncher.launch(
                        ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt(context.getString(R.string.profiles_scan_prompt))
                            setBeepEnabled(false)
                            setOrientationLocked(false)
                        },
                    )
                }
                AddSheetRow(stringResource(R.string.profiles_add_subscription)) {
                    showAddSheet = false
                    showImport = true
                }
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    // A Snackbar instead of a banner above the list: the banner pushed the
    // whole list down every time a message appeared, which is a layout shift in
    // exchange for information that is transient anyway.
    val transientMessage = state.message
        ?.takeIf { it !is ProfilesMessage.NoRegionsFound }
        ?.text()
    LaunchedEffect(transientMessage) {
        if (transientMessage != null) {
            snackbarHostState.showSnackbar(transientMessage)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profiles_title)) },
                actions = {
                    // Whole-list test, as an icon: the text button cost a row
                    // above the list for something used occasionally.
                    IconButton(onClick = { onTestLatency { msg -> viewModel.postMessage(msg) } }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.profiles_test_all),
                        )
                    }
                    // Group actions are not filters, so they do not belong in
                    // the filter row: that row is now only "which nodes".
                    Box {
                        IconButton(onClick = { showGroupMenu = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.profiles_group_actions),
                            )
                        }
                        DropdownMenu(
                            expanded = showGroupMenu,
                            onDismissRequest = { showGroupMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.profiles_group_new)) },
                                onClick = {
                                    showGroupMenu = false
                                    creatingGroup = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.profiles_group_by_region)) },
                                onClick = {
                                    showGroupMenu = false
                                    viewModel.groupByRegion()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.profiles_add))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.message?.let { message ->
                if (message is ProfilesMessage.NoRegionsFound) {
                    // A dialog, not the banner. "By region" is the one action
                    // whose failure is "nothing appears", so a strip at the top
                    // of the list is exactly the wrong shape for it — the user
                    // tapped a button and watched the list, not the header.
                    AlertDialog(
                        onDismissRequest = viewModel::dismissMessage,
                        title = { Text(stringResource(R.string.profiles_group_by_region)) },
                        text = { Text(message.text()) },
                        confirmButton = {
                            TextButton(onClick = viewModel::dismissMessage) {
                                Text(stringResource(R.string.profiles_dialog_ok))
                            }
                        },
                    )
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
                        inUse = state.selectedGroupId == group.id,
                    )
                }
            }
            when {
                !state.hasProfiles -> EmptyState(
                    title = stringResource(R.string.profiles_empty_title),
                    body = stringResource(R.string.profiles_empty_body),
                )

                // Nodes exist but the filter hides them: say so, rather than
                // reusing "No nodes yet", which would be a lie.
                state.hasNoMatches -> EmptyState(
                    title = stringResource(R.string.profiles_no_matches_title),
                    body = stringResource(R.string.profiles_no_matches_body),
                )

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
                            SwipeToDeleteRow(onDelete = { viewModel.deleteWithUndo(profile) }) {
                                NodeRow(
                                    profile = profile,
                                    health = state.health[profile.id],
                                    groupLabel = groupNames[profile.groupId]
                                        ?: stringResource(R.string.profiles_group_none),
                                    selected = profile.id == state.selectedId,
                                    onClick = { viewModel.select(profile.id) },
                                    onLongClick = { rowMenu = profile },
                                    onTestClick = {
                                        onTestNode(profile.id) { msg -> viewModel.postMessage(msg) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
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
        )
    }

    state.share?.let { target ->
        ShareDialog(target = target, onDismiss = viewModel::dismissShare)
    }


    groupMenu?.let { menuGroup ->
        // Read through to the live row: the captured value is a snapshot, so
        // changing the mode would otherwise leave the chips showing the old one.
        val group = state.groups.firstOrNull { it.id == menuGroup.id } ?: menuGroup
        val inUse = state.selectedGroupId == group.id
        AlertDialog(
            onDismissRequest = { groupMenu = null },
            title = { Text(group.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                    Text(stringResource(R.string.profiles_group_menu_body, group.profileIds.size))

                    Text(
                        text = stringResource(R.string.profiles_group_mode_title),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                        GroupMode.entries.forEach { mode ->
                            FilterChip(
                                selected = group.mode == mode,
                                onClick = { viewModel.setGroupMode(group, mode) },
                                label = { Text(stringResource(mode.labelRes())) },
                            )
                        }
                    }

                    // Say why the button is unavailable rather than leaving a
                    // dead control with no explanation.
                    when {
                        group.profileIds.isEmpty() -> Hint(
                            stringResource(R.string.profiles_group_connect_empty_hint),
                        )

                        group.mode == GroupMode.MANUAL -> Hint(
                            stringResource(R.string.profiles_group_connect_folder_hint),
                        )
                    }

                    TextButton(
                        enabled = group.mode != GroupMode.MANUAL && group.profileIds.isNotEmpty(),
                        onClick = {
                            if (inUse) viewModel.stopUsingGroup() else viewModel.selectGroup(group)
                            groupMenu = null
                        },
                    ) {
                        Text(
                            stringResource(
                                if (inUse) {
                                    R.string.profiles_group_connect_stop
                                } else {
                                    R.string.profiles_group_connect
                                },
                            ),
                        )
                    }

                    // Only a subscription-backed group has something to fetch;
                    // a folder would just be a button that does nothing.
                    if (!group.subscriptionUrl.isNullOrBlank()) {
                        TextButton(
                            enabled = !state.importing,
                            onClick = {
                                groupMenu = null
                                viewModel.refreshSubscription(group)
                            },
                        ) {
                            Text(stringResource(R.string.profiles_group_refresh))
                        }
                    }

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

    rowMenu?.let { profile ->
        AlertDialog(
            onDismissRequest = { rowMenu = null },
            title = { Text(stringResource(R.string.profiles_row_menu_title, profile.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                    TextButton(
                        onClick = {
                            rowMenu = null
                            onEditNode(profile.type, profile.id)
                        },
                    ) {
                        Text(stringResource(R.string.profiles_row_edit))
                    }
                    TextButton(
                        onClick = {
                            rowMenu = null
                            viewModel.shareNode(profile)
                        },
                    ) {
                        Text(stringResource(R.string.profiles_row_share))
                    }
                    TextButton(
                        onClick = {
                            rowMenu = null
                            movingProfile = profile
                        },
                    ) {
                        Text(stringResource(R.string.profiles_row_move))
                    }
                    TextButton(
                        onClick = {
                            rowMenu = null
                            viewModel.deleteWithUndo(profile)
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.profiles_delete, profile.name),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { rowMenu = null }) {
                    Text(stringResource(R.string.profiles_group_cancel))
                }
            },
        )
    }

    if (pickingProtocol) {
        AlertDialog(
            onDismissRequest = { pickingProtocol = false },
            title = { Text(stringResource(R.string.profiles_new_node_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                    ProfileType.entries.filter { it != ProfileType.OTHER }.forEach { type ->
                        TextButton(
                            onClick = {
                                pickingProtocol = false
                                onNewNode(type)
                            },
                        ) {
                            Text(type.editorLabel())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingProtocol = false }) {
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
                    // its nodes with it, and that is not obvious. A region
                    // group takes nothing with it, which is equally not obvious.
                    when {
                        group.region != null ->
                            stringResource(R.string.profiles_group_delete_body_derived, group.name)
                        group.profileIds.isEmpty() ->
                            stringResource(R.string.profiles_group_delete_body_empty, group.name)
                        else -> stringResource(
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
                // Paste only. The scan entry lives in the add sheet, one level
                // up: two ways to open the same scanner from the same flow is
                // one button too many, and this one is the harder to find.
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
/**
 * The share sheet: the node's link, a QR code for it, and a copy button.
 *
 * The QR is the point — a link that has to be typed by hand is not really
 * shareable — so when a protocol has no link format the sheet says so instead
 * of drawing a code that would scan into an empty string.
 */
@Composable
private fun ShareDialog(target: ShareTarget, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles_share_title, target.name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val link = target.link
                if (link == null) {
                    Text(
                        text = stringResource(R.string.profiles_share_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }

                QrCode(content = link, modifier = Modifier.size(QR_SIZE))
                Text(
                    text = link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        writeClipboard(context, link, "Starveil node")
                        copied = true
                    },
                ) {
                    Text(
                        stringResource(
                            if (copied) R.string.profiles_share_copied else R.string.profiles_share_copy,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profiles_group_cancel))
            }
        },
    )
}

@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    // Encoding a few hundred bytes into a bitmap is not free, and the link does
    // not change while the sheet is open.
    val image = remember(content) {
        runCatching {
            BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, QR_PX, QR_PX)
        }.getOrNull()
    }
    if (image == null) {
        // Too long for a QR code, or the encoder refused. The link is still on
        // screen to copy, so say what happened rather than showing a blank box.
        Text(
            text = stringResource(R.string.profiles_share_no_qr),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Image(
        bitmap = image.asImageBitmap(),
        contentDescription = stringResource(R.string.profiles_share_qr_description),
        modifier = modifier,
    )
}

/** Rendered at 512px and scaled down, so the code stays crisp when scanned. */
private const val QR_PX = 512
private val QR_SIZE = 200.dp


/**
 * Cheap "could this be a node?" check, used only to decide whether to prefill
 * from the clipboard. Deliberately permissive: anything with a scheme.
 */
private fun looksImportable(text: String): Boolean =
    text.contains("://") || text.startsWith("http", ignoreCase = true)

/**
 * A message as the user reads it.
 *
 * The one place this feature's copy lives, so the view model can stay free of
 * it (C1) and the wording sits with the rest of the app's.
 */
@Composable
private fun ProfilesMessage.text(): String = when (this) {
    is ProfilesMessage.AddedNode -> stringResource(R.string.profiles_msg_added, name)
    is ProfilesMessage.ImportedNodes ->
        if (dropped == 0) {
            stringResource(R.string.profiles_msg_imported, count)
        } else {
            stringResource(R.string.profiles_msg_imported_skipped, count, dropped)
        }
    is ProfilesMessage.ImportFailed -> stringResource(R.string.profiles_msg_import_failed, reason)
    is ProfilesMessage.Refreshed ->
        if (dropped == 0) {
            stringResource(R.string.profiles_msg_refreshed, count)
        } else {
            stringResource(R.string.profiles_msg_refreshed_skipped, count, dropped)
        }
    is ProfilesMessage.RefreshFailed -> stringResource(R.string.profiles_msg_refresh_failed, reason)
    is ProfilesMessage.GroupedByRegion -> stringResource(R.string.profiles_msg_grouped, regions)
    ProfilesMessage.NoRegionsFound -> stringResource(R.string.profiles_msg_no_regions)
    is ProfilesMessage.External -> text
}

@OptIn(ExperimentalFoundationApi::class)
/**
 * Swipe left to delete, with the reversal offered in the app's one Snackbar (K2).
 *
 * The delete happens on the gesture rather than after a confirmation: it is
 * reversible, so a dialog in front of it would be asking permission for
 * something that can be taken back. The row leaving is the feedback, and the
 * Snackbar is the way back.
 *
 * This replaced a trailing delete button, which existed because "a node carries
 * credentials and re-importing one is not always possible". That is still true,
 * and it is exactly what the undo answers: the credentials are not lost, they
 * are one tap away.
 */
@Composable
private fun SwipeToDeleteRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            // Never let the box *hold* a dismissed state — the row leaves
            // because the list changed, not because this state says so.
            //
            // Returning true here is the obvious version and it is wrong: the
            // state is saveable, keyed by the LazyColumn item key, and the undo
            // restores the node under the *same* id on purpose. So a restored
            // row inherits the dismissed state and comes back as a blank red
            // card with a bin icon — permanent, and it survives navigation.
            // That is exactly what happened on device.
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = StarveilSpacing.lg)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    // Decorative: the row already announces itself, and a
                    // second "Delete <node>" would read it out twice.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        content()
    }
}

/**
 * The node card (§6.3): name, group label, status dot, latency mini-curve and
 * the latency number.
 *
 * A card rather than a dense list row because it has to carry a curve, and a
 * curve beside a name needs the vertical room a row does not have — hence the
 * spec's 72dp minimum.
 */
@Composable
private fun NodeRow(
    profile: Profile,
    health: NodeHealth?,
    groupLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTestClick: () -> Unit,
) {
    val reading = latencyReading(profile, health)
    Surface(
        // Long-press moves the node between groups; a tap stays the selection.
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NODE_CARD_MIN_HEIGHT)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button,
                // Long-press is invisible to a screen reader unless it is
                // named; without this the action simply does not exist there.
                onLongClickLabel = stringResource(R.string.profiles_node_long_press),
            ),
        shape = RoundedCornerShape(NODE_CARD_CORNER),
        // Selection is a border plus a halo, never a different fill: the fill
        // says "this is a surface", and giving it a second meaning would make
        // the card's own colour ambiguous (§2.1, §6.3).
        color = if (selected) {
            lerp(
                MaterialTheme.colorScheme.surfaceVariant,
                StarveilColors.GlowPrimary,
                SELECTED_HALO_ALPHA,
            )
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = if (selected) SELECTED_BORDER_WIDTH else IDLE_BORDER_WIDTH,
            color = if (selected) StarveilColors.BorderGlow else StarveilColors.Border,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NODE_CARD_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(status = health?.status ?: NodeStatus.UNTESTED)
                    Spacer(modifier = Modifier.width(StarveilSpacing.sm))
                    Text(
                        text = profile.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(StarveilSpacing.xs))
                Text(
                    // Group first: it is what the user filed the node under. The
                    // host is the detail they read second.
                    text = "$groupLabel · ${profile.type.name.lowercase()} " +
                        "· ${profile.server}:${profile.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(StarveilSpacing.md))
            Column(horizontalAlignment = Alignment.End) {
                // Only once there is a shape to draw: the sparkline and the
                // chart agree on the threshold, so the card cannot imply a
                // curve the chart would refuse to draw.
                if (health != null && !health.collecting) {
                    LatencySparkline(
                        health = health,
                        modifier = Modifier.width(SPARKLINE_WIDTH),
                        height = SPARKLINE_HEIGHT,
                    )
                    Spacer(modifier = Modifier.height(StarveilSpacing.xs))
                }
                Text(
                    text = when (reading) {
                        is LatencyReading.Value ->
                            stringResource(R.string.profiles_latency_ms, reading.ms)
                        LatencyReading.Timeout ->
                            stringResource(R.string.profiles_latency_timeout)
                        LatencyReading.Untested ->
                            stringResource(R.string.profiles_latency_untested)
                    },
                    // The badge is the node's own measurement, so tapping it
                    // measures *this* node — testing the selected one from here
                    // would quietly measure a different node than the one under
                    // the finger.
                    modifier = Modifier
                        .clip(RoundedCornerShape(LATENCY_TOUCH_CORNER))
                        .clickable(role = Role.Button, onClick = onTestClick)
                        .padding(horizontal = StarveilSpacing.sm, vertical = StarveilSpacing.xs),
                    style = MaterialTheme.typography.labelSmall,
                    // Dimmed, not red, when there is no reading: the status dot
                    // already says "down", and colouring the number too would be
                    // the same signal twice (§6.3).
                    color = when (reading) {
                        is LatencyReading.Value -> MaterialTheme.colorScheme.onSurfaceVariant
                        LatencyReading.Timeout, LatencyReading.Untested ->
                            StarveilColors.TextDisabled
                    },
                )
            }
        }
    }
}

/**
 * The status dot (§6.3): green, grey, red.
 *
 * Carries a description because colour alone is not a signal for anyone using a
 * screen reader — the dot is the only place the window's verdict appears.
 */
@Composable
private fun StatusDot(status: NodeStatus) {
    val color = when (status) {
        NodeStatus.UP -> StarveilColors.Connected
        NodeStatus.DOWN -> StarveilColors.Blocked
        NodeStatus.UNTESTED -> StarveilColors.Disconnected
    }
    val description = when (status) {
        NodeStatus.UP -> stringResource(R.string.profiles_status_up)
        NodeStatus.DOWN -> stringResource(R.string.profiles_status_down)
        NodeStatus.UNTESTED -> stringResource(R.string.profiles_status_untested)
    }
    Box(
        modifier = Modifier
            .size(STATUS_DOT_SIZE)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = description },
    )
}

/** What the card's latency number says, and whether it is a real reading. */
internal sealed interface LatencyReading {
    data class Value(val ms: Long) : LatencyReading

    /** The last probe did not come back. */
    data object Timeout : LatencyReading

    /** Nothing has been measured, by the sampler or by hand. */
    data object Untested : LatencyReading
}

/**
 * Which number the card shows.
 *
 * The curve's own value wins when there is one, so the number and the line
 * cannot disagree. A failed *latest* probe takes precedence over both: the
 * smoothed value is still meaningful, but the card is reporting "right now",
 * and "right now" is that the node did not answer.
 *
 * `Profile.latencyMs` is the fallback, not the first choice. It is what the
 * manual "test latency" button writes, and it is the only reading a node has
 * until the sampler has collected a few — showing "untested" next to a node the
 * user just measured would be a lie.
 */
internal fun latencyReading(profile: Profile, health: NodeHealth?): LatencyReading {
    // Read once into a local: `NodeHealth` is in another module, so its
    // properties cannot be smart-cast after a null check.
    val ema = health?.emaMs
    return when {
        health?.latest?.reachable == false -> LatencyReading.Timeout
        ema != null -> LatencyReading.Value(ema)
        profile.latencyMs >= 0 -> LatencyReading.Value(profile.latencyMs)
        else -> LatencyReading.Untested
    }
}

/** §6.3 card metrics. */
private val NODE_CARD_MIN_HEIGHT = 72.dp
private val NODE_CARD_CORNER = 16.dp
private val NODE_CARD_PADDING = 16.dp
private val IDLE_BORDER_WIDTH = 1.dp
private val SELECTED_BORDER_WIDTH = 2.dp
private val STATUS_DOT_SIZE = 8.dp
private val SPARKLINE_WIDTH = 64.dp
private val SPARKLINE_HEIGHT = 20.dp

/** §2.1's "GlowPrimaryDim": the halo a selected card is lit with. */
private const val SELECTED_HALO_ALPHA = 0.16f

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
    /** The tunnel goes through this group, as opposed to the list being filtered to it. */
    inUse: Boolean = false,
) {
    // Named apart from the semantics property it feeds: inside the `semantics`
    // block an unqualified `selected` would resolve to the receiver's, not to
    // this parameter.
    val isSelected = selected
    val stateText = when {
        inUse -> stringResource(R.string.profiles_group_state_in_use)
        isSelected -> stringResource(R.string.profiles_group_state_selected)
        else -> null
    }
    Surface(
        modifier = Modifier
            // Material's 48dp minimum applies to its own components, not to a
            // raw Surface; the chip's padding alone came out around 40dp.
            .heightIn(min = MIN_TOUCH_TARGET)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button,
            )
            .semantics {
                this.selected = isSelected
                // "In use" and "filtered to" are different states that look
                // similar; without this TalkBack reads only the label.
                if (stateText != null) stateDescription = stateText
            },
        shape = RoundedCornerShape(percent = 50),
        // Theme colours, not the HUD's dark constants. In dark mode primary is
        // GlowPrimary and surfaceVariant is Raised, so this is the spec's
        // "GlowPrimary 20%" exactly; in light mode the same two tokens are a
        // deep blue and a pale slate, which is what keeps the row looking like
        // it belongs to the page instead of a dark-mode island.
        // Mixed with the chip's own surface rather than alpha-composited over
        // the page: a 20% wash of a dark primary over a light page moved the
        // fill by nine levels out of 255, which is not a difference anyone can
        // see. Mixing keeps the same recipe meaningful in both themes.
        color = if (selected) {
            lerp(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primary,
                GROUP_SELECTED_MIX,
            )
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        // The accent lives on the border, not in the label: primary as text on
        // a 24% primary fill measured 3.55:1 in the light scheme, and a chip's
        // label is 12sp, so it has to clear 4.5. The border says "selected"
        // without spending the label's contrast.
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            null
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = StarveilSpacing.md,
                vertical = StarveilSpacing.sm,
            ),
        ) {
            if (inUse) {
                // A dot rather than another chip colour: `selected` here means
                // "the list is filtered to this group", which is a different
                // thing from "the tunnel goes through it".
                Box(
                    modifier = Modifier
                        .size(IN_USE_DOT)
                        .clip(CircleShape)
                        .background(StarveilColors.Connected),
                )
                Spacer(modifier = Modifier.width(StarveilSpacing.xs))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * Selected chip fill: how far the surface moves toward primary.
 *
 * The spec says "GlowPrimary 20%", which was written for a dark page; 24% is
 * where the same idea survives on a light one.
 */
private const val GROUP_SELECTED_MIX = 0.24f

/** Tap target padding around the latency badge. */
private val LATENCY_TOUCH_CORNER = 8.dp

/** The "tunnel goes through this group" marker on a chip. */
private val IN_USE_DOT = 6.dp

/** The platform's minimum touch target; chips and other raw surfaces use it. */
private val MIN_TOUCH_TARGET = 48.dp

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun GroupMode.labelRes(): Int = when (this) {
    GroupMode.MANUAL -> R.string.profiles_group_mode_folder
    GroupMode.SELECTOR -> R.string.profiles_group_mode_selector
    GroupMode.URL_TEST -> R.string.profiles_group_mode_urltest
}

private fun ProfileType.editorLabel(): String = when (this) {
    ProfileType.VLESS -> "VLESS"
    ProfileType.VMESS -> "VMess"
    ProfileType.TROJAN -> "Trojan"
    ProfileType.SHADOWSOCKS -> "Shadowsocks"
    ProfileType.HYSTERIA2 -> "Hysteria2"
    ProfileType.TUIC -> "TUIC"
    ProfileType.WIREGUARD -> "WireGuard"
    ProfileType.OTHER -> "Node"
}

/** One line in the add sheet: a title, and a tap. */
@Composable
private fun AddSheetRow(title: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = StarveilSpacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(vertical = StarveilSpacing.sm),
        )
    }
}
