package com.justnpcx.starveil.feature.profiles

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.justnpcx.starveil.core.ui.components.StarveilTextField
import com.justnpcx.starveil.core.subscription.DocumentFormat
import com.justnpcx.starveil.core.subscription.ImportFailure
import com.justnpcx.starveil.core.subscription.SubscriptionFailure
import com.justnpcx.starveil.core.model.NodeHealth
import com.justnpcx.starveil.core.model.NodeStatus
import com.justnpcx.starveil.core.ui.components.LatencySparkline
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.components.ChipSurface
import com.justnpcx.starveil.core.ui.components.StarveilChip
import com.justnpcx.starveil.core.ui.components.StarveilChipRow
import com.justnpcx.starveil.core.ui.components.StarveilDivider
import com.justnpcx.starveil.core.ui.components.StarveilFab
import com.justnpcx.starveil.core.ui.components.StarveilIconAction
import com.justnpcx.starveil.core.ui.components.StarveilPageHeader
import com.justnpcx.starveil.core.ui.components.StarveilSearchField
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import com.justnpcx.starveil.core.model.GroupMode
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import com.justnpcx.starveil.core.ui.theme.MonoFamily
import com.justnpcx.starveil.core.ui.theme.StarveilShapes
import com.justnpcx.starveil.core.ui.theme.StarveilType
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
    /**
     * A link that arrived from outside the app, to import on arrival.
     *
     * Passed in rather than read here: the inbox lives in :app, and this screen
     * must stay usable without one. [onLinkHandled] is called once it has been
     * taken, so the same link cannot be imported again when the screen is
     * recomposed or revisited.
     */
    incomingLink: String? = null,
    onLinkHandled: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showImport by remember { mutableStateOf(false) }

    // A share link handed to the app. Keyed on the link so a second one while
    // this screen is open is imported too, and reported through the same
    // message channel as a pasted one — there is no second code path.
    LaunchedEffect(incomingLink) {
        val link = incomingLink ?: return@LaunchedEffect
        viewModel.import(link)
        onLinkHandled()
    }
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
    // Whether the timed-out nodes are back in the list. Remembered for the
    // visit, not persisted: the point of folding them is that the next visit is
    // short again.
    var showTimedOut by remember { mutableStateOf(false) }
    // The menu's delete is a tap, not a gesture: confirm it. The swipe keeps
    // its instant delete + undo, because a swipe cannot be a stray tap.
    var nodeToDelete by remember { mutableStateOf<Profile?>(null) }
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
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            containerColor = StarveilColors.surfaceSolid,
            shape = StarveilShapes.sheet,
            dragHandle = null,
        ) {
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
            StarveilPageHeader(title = stringResource(R.string.profiles_title)) {
                    // Whole-list test, as an icon: the text button cost a row
                    // above the list for something used occasionally.
                    StarveilIconAction(
                        icon = StarveilIcons.Refresh,
                        contentDescription = stringResource(R.string.profiles_test_selected),
                        onClick = { onTestLatency { msg -> viewModel.postMessage(msg) } },
                    )
                    // Group actions are not filters, so they do not belong in
                    // the filter row: that row is now only "which nodes".
                    Box {
                        StarveilIconAction(
                            icon = StarveilIcons.More,
                            contentDescription = stringResource(R.string.profiles_group_actions),
                            onClick = { showGroupMenu = true },
                        )
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
            }
        },
        // Transparent: the ambient gradient behind the app is the page.
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            StarveilFab(
                contentDescription = stringResource(R.string.profiles_add),
                onClick = { showAddSheet = true },
            )
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
            // Above the group chips, and only once there is something to
            // search: an empty list already says "import something", and a
            // search field over it would be a control that cannot do anything.
            if (state.hasProfiles) {
                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    onClear = viewModel::clearQuery,
                )
            }
            // Always shown, even with no groups: creating the first one has to
            // have somewhere to live.
            StarveilChipRow {
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
            // The quota the provider published, when it published one.
            // Filtered to a group: that group's number or nothing. Showing
            // another subscription's quota here would be worse than showing
            // none, because the number looks like it belongs to what is on
            // screen. Unfiltered, the node in force is the one being described.
            val usage = if (state.groupFilter != null) {
                state.usage[state.groupFilter]
            } else {
                state.usage[state.selectedGroupId]
            }
            if (usage != null) {
                UsageBanner(info = usage)
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

                // Same, for the search: the sentence has to name what was
                // searched for, or it reads like the group is empty.
                state.hasNoSearchMatches -> EmptyState(
                    title = stringResource(R.string.profiles_no_search_title),
                    body = stringResource(R.string.profiles_no_search_body, state.query),
                )

                // One card holds the whole list: a column of separate cards
                // reads as a stack of unrelated things, and the rows are one
                // list. The FAB clears the last row by its own height.
                else -> StarveilCard(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = StarveilSpacing.pageMargin),
                ) {
                    // Timed-out nodes are hidden by default: a subscription
                    // with hundreds of dead rows is a list you scroll once and
                    // never again. The rows are not deleted — a footer says how
                    // many are out of the way and brings them back.
                    val timedOut = remember(state.profiles, state.health) {
                        state.profiles.filter { state.health[it.id]?.status == NodeStatus.DOWN }
                    }
                    val shown = if (showTimedOut) state.profiles else
                        state.profiles.filterNot { state.health[it.id]?.status == NodeStatus.DOWN }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = StarveilSpacing.fabClearance),
                    ) {
                    itemsIndexed(shown, key = { _, profile -> profile.id }) { index, profile ->
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
                                    showDivider = index != shown.lastIndex || timedOut.isNotEmpty(),
                                    onClick = { viewModel.select(profile.id) },
                                    onLongClick = { rowMenu = profile },
                                    onTestClick = {
                                        onTestNode(profile.id) { msg -> viewModel.postMessage(msg) }
                                    },
                                )
                            }
                        }
                    }
                    if (timedOut.isNotEmpty()) {
                        item {
                            StarveilDivider(startInset = StarveilSpacing.lg)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        role = Role.Button,
                                        onClick = { showTimedOut = !showTimedOut },
                                    )
                                    .padding(
                                        horizontal = StarveilSpacing.lg,
                                        vertical = StarveilSpacing.md,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(
                                        if (showTimedOut) {
                                            R.string.profiles_timed_out_shown
                                        } else {
                                            R.string.profiles_timed_out_hidden
                                        },
                                        timedOut.size,
                                    ),
                                    style = StarveilType.label,
                                    color = StarveilColors.accentText,
                                )
                            }
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
                        style = StarveilType.label,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                        GroupMode.entries.forEach { mode ->
                            StarveilChip(
                                label = stringResource(mode.labelRes()),
                                selected = group.mode == mode,
                                onClick = { viewModel.setGroupMode(group, mode) },
                                surface = ChipSurface.Dialog,
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
                            color = StarveilColors.errText,
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

    // The row's actions, as a sheet rather than a dialog: a dialog puts four
    // text buttons in the middle of the screen, and the sheet is where the rest
    // of this page's menus live. Edit is first because it is what a long-press
    // is usually for.
    rowMenu?.let { profile ->
        ModalBottomSheet(
            onDismissRequest = { rowMenu = null },
            containerColor = StarveilColors.surfaceSolid,
            dragHandle = null,
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                Text(
                    text = stringResource(R.string.profiles_row_menu_title, profile.name),
                    style = StarveilType.tech,
                    color = StarveilColors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = StarveilSpacing.pageMargin,
                        end = StarveilSpacing.pageMargin,
                        top = StarveilSpacing.xl,
                        bottom = StarveilSpacing.sm,
                    ),
                )
                StarveilSheetRow(stringResource(R.string.profiles_row_edit)) {
                    rowMenu = null
                    onEditNode(profile.type, profile.id)
                }
                StarveilSheetRow(stringResource(R.string.profiles_row_share)) {
                    rowMenu = null
                    viewModel.shareNode(profile)
                }
                StarveilSheetRow(stringResource(R.string.profiles_row_move)) {
                    rowMenu = null
                    movingProfile = profile
                }
                StarveilSheetRow(
                    text = stringResource(R.string.profiles_delete, profile.name),
                    destructive = true,
                ) {
                    rowMenu = null
                    nodeToDelete = profile
                }
            }
        }
    }

    nodeToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { nodeToDelete = null },
            title = { Text(stringResource(R.string.profiles_node_delete_title)) },
            text = { Text(stringResource(R.string.profiles_node_delete_body, profile.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWithUndo(profile)
                        nodeToDelete = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.profiles_delete, profile.name),
                        color = StarveilColors.errText,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { nodeToDelete = null }) {
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
                        color = StarveilColors.errText,
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
            StarveilTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.profiles_group_name_label),
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
                StarveilTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        hint = null
                    },
                    label = stringResource(R.string.profiles_import_hint),
                                        singleLine = false,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                hint?.let {
                    Text(
                        text = it,
                        style = StarveilType.label,
                        color = StarveilColors.errText,
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
                        style = StarveilType.label,
                        color = StarveilColors.ink2,
                    )
                    return@Column
                }

                QrCode(content = link, modifier = Modifier.size(QR_SIZE))
                Text(
                    text = link,
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
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
            style = StarveilType.label,
            color = StarveilColors.ink2,
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
    is ProfilesMessage.ImportFailed -> stringResource(R.string.profiles_msg_import_failed, failure.text())
    is ProfilesMessage.Refreshed ->
        if (dropped == 0) {
            stringResource(R.string.profiles_msg_refreshed, count)
        } else {
            stringResource(R.string.profiles_msg_refreshed_skipped, count, dropped)
        }
    is ProfilesMessage.RefreshFailed -> stringResource(R.string.profiles_msg_refresh_failed, reason.text())
    is ProfilesMessage.GroupedByRegion -> stringResource(R.string.profiles_msg_grouped, regions)
    ProfilesMessage.NoRegionsFound -> stringResource(R.string.profiles_msg_no_regions)
    is ProfilesMessage.External -> text
}

/**
 * A failure as the user reads it.
 *
 * The core returns data, not sentences (C1): a line number is useful only if
 * it can be phrased in the reader's language, and a parser message can quote
 * the body — which carries credentials.
 */
@Composable
private fun ImportFailure.text(): String = when (this) {
    ImportFailure.NothingToImport -> stringResource(R.string.profiles_failure_nothing)
    ImportFailure.LinkUnparseable -> stringResource(R.string.profiles_failure_link)
    is ImportFailure.Subscription -> reason.text()
}

@Composable
private fun SubscriptionFailure.text(): String = when (this) {
    SubscriptionFailure.Network -> stringResource(R.string.profiles_failure_network)
    is SubscriptionFailure.Http -> stringResource(R.string.profiles_failure_http, code)
    is SubscriptionFailure.MalformedDocument -> {
        // A local copy: `line` comes from another module and cannot be smart-cast.
        val at = line
        when {
            format == DocumentFormat.YAML && at != null ->
                stringResource(R.string.profiles_failure_yaml_line, at)
            format == DocumentFormat.JSON && at != null ->
                stringResource(R.string.profiles_failure_json_line, at)
            else -> stringResource(R.string.profiles_failure_document)
        }
    }
    SubscriptionFailure.NoUsableNodes -> stringResource(R.string.profiles_failure_no_nodes)
    SubscriptionFailure.SaveFailed -> stringResource(R.string.profiles_failure_save)
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
        // Drawn only while the row is actually moving. SwipeToDismissBox keeps
        // the background composed at rest, so a filled background is a red
        // rectangle behind every row — it showed at the ends of the card and
        // tinted the whole list warm.
        backgroundContent = {
            // The target value, not progress and not the offset: progress is
            // non-zero at rest in this version (the row still painted red), and
            // requireOffset() throws when read during composition — it crashed
            // the app on the first frame after this screen opened. targetValue
            // is a plain state read and is Settled until a drag commits.
            if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) {
                return@SwipeToDismissBox
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StarveilColors.err.copy(alpha = 0.16f)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    // Decorative: the row already announces itself, and a
                    // second "Delete <node>" would read it out twice.
                    contentDescription = null,
                    tint = StarveilColors.err,
                    modifier = Modifier.padding(end = StarveilSpacing.lg),
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
    showDivider: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTestClick: () -> Unit,
) {
    val reading = latencyReading(profile, health)
    val wash = StarveilColors.accentWash
    val accent = StarveilColors.accent
    val line = StarveilColors.divider
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // The selected row is marked twice: a cyan rule on its leading
                // edge and a 12% wash behind it. One cue alone is not enough on
                // a list where the row above is also a row.
                if (selected) {
                    drawRect(color = wash)
                    drawRect(
                        color = accent,
                        topLeft = Offset.Zero,
                        size = Size(2.dp.toPx(), size.height),
                    )
                }
                if (showDivider) {
                    drawLine(
                        color = line,
                        start = Offset(StarveilSpacing.lg.toPx(), size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button,
                // Long-press is invisible to a screen reader unless it is
                // named; without this the action simply does not exist there.
                onLongClickLabel = stringResource(R.string.profiles_node_long_press),
            )
            .heightIn(min = NODE_ROW_MIN_HEIGHT)
            // The FAB floats over this list's bottom-right corner, so the row
            // reserves its footprint on the right: 56dp of button plus the
            // 16dp it sits from the screen edge. Without the gutter a row's
            // latency — the one number on the right — passes under the button
            // as the list scrolls, and "under the button" means unreadable and
            // untappable at the same time.
            .padding(
                start = StarveilSpacing.lg,
                top = StarveilSpacing.md,
                end = FAB_GUTTER,
                bottom = StarveilSpacing.md,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // The name and the number share a baseline: they are the row's two
            // readings, and a number that floats at the top-right of the row is
            // the thing this replaces.
            Row(modifier = Modifier.fillMaxWidth()) {
                StatusDot(
                    status = health?.status ?: NodeStatus.UNTESTED,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Spacer(modifier = Modifier.width(StarveilSpacing.sm))
                Text(
                    // The flag is part of the display name and sits on the same
                    // baseline as the rest of it.
                    text = profile.displayName(),
                    style = StarveilType.headline,
                    color = StarveilColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                )
                Spacer(modifier = Modifier.width(StarveilSpacing.md))
                // A fixed column, right-aligned: the unit must never be clipped
                // by a long node name, and the column must not jump when a
                // reading changes width. The number is the node's own
                // measurement, so tapping it measures *this* node — testing the
                // selected one from here would measure a different node than the
                // one under the finger.
                Row(
                    modifier = Modifier
                        .width(LATENCY_COLUMN_WIDTH)
                        .clip(RoundedCornerShape(LATENCY_TOUCH_CORNER))
                        .clickable(role = Role.Button, onClick = onTestClick)
                        .padding(horizontal = StarveilSpacing.xs),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = when (reading) {
                            is LatencyReading.Value -> reading.ms.toString()
                            LatencyReading.Timeout ->
                                stringResource(R.string.profiles_latency_timeout)
                            LatencyReading.Untested ->
                                stringResource(R.string.profiles_latency_untested)
                        },
                        modifier = Modifier.alignByBaseline(),
                        style = if (reading is LatencyReading.Value) {
                            StarveilType.numInline
                        } else {
                            StarveilType.tech
                        },
                        // Dimmed, not red, when there is no reading: the status
                        // dot already says "down", and colouring the number too
                        // would be the same signal twice.
                        color = if (reading is LatencyReading.Value) {
                            StarveilColors.ink
                        } else {
                            StarveilColors.ink3
                        },
                        maxLines = 1,
                    )
                    if (reading is LatencyReading.Value) {
                        Text(
                            text = " ms",
                            style = StarveilType.tech,
                            color = StarveilColors.ink2,
                            modifier = Modifier.alignByBaseline(),
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(StarveilSpacing.xs))
            Row(modifier = Modifier.padding(start = 16.dp)) {
                // Group first: it is what the user filed the node under. The
                // protocol is the mono face because it is a name, not prose,
                // and the address is the detail read last.
                Text(
                    text = groupLabel,
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                    maxLines = 1,
                )
                Text(
                    text = " · ",
                    style = StarveilType.label,
                    color = StarveilColors.ink3,
                )
                Text(
                    text = profile.type.name.lowercase(),
                    style = StarveilType.label.copy(fontFamily = MonoFamily),
                    color = StarveilColors.ink2,
                    maxLines = 1,
                )
                Text(
                    text = " · ",
                    style = StarveilType.label,
                    color = StarveilColors.ink3,
                )
                Text(
                    text = profile.server + ":" + profile.port,
                    style = StarveilType.label,
                    color = StarveilColors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
private fun StatusDot(status: NodeStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        NodeStatus.UP -> StarveilColors.ok
        NodeStatus.DOWN -> StarveilColors.err
        NodeStatus.UNTESTED -> StarveilColors.ink3
    }
    val description = when (status) {
        NodeStatus.UP -> stringResource(R.string.profiles_status_up)
        NodeStatus.DOWN -> stringResource(R.string.profiles_status_down)
        NodeStatus.UNTESTED -> stringResource(R.string.profiles_status_untested)
    }
    Box(
        modifier = modifier
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
private val NODE_ROW_MIN_HEIGHT = 64.dp
private val SELECTED_BORDER_WIDTH = 2.dp
private val STATUS_DOT_SIZE = 8.dp
private val SPARKLINE_WIDTH = 64.dp
private val SPARKLINE_HEIGHT = 20.dp

/** §2.1's "GlowPrimaryDim": the halo a selected card is lit with. */
private const val SELECTED_HALO_ALPHA = 0.16f

/**
 * A group filter chip (design system §6.4): rounded rect, raised surface when
 * idle, brand glow when selected. The radius is fixed, not a percentage: a
 * percent radius follows the chip's height and width, so "全部" (two glyphs)
 * came out a circle while "Subscription" came out a capsule — one component
 * with two shapes on the same row.
 *
 * Long-press opens group management. A tap has to stay the filter, so the
 * destructive actions cannot live there.
 */
/**
 * The node list's search field.
 *
 * Its own row rather than the app bar's title slot: the bar already carries
 * three actions, and a title that turns into a field makes the two tabs that
 * are not this one feel like a mode change. `OutlinedTextField` at 48dp is the
 * same height as the chip row below it, so the two read as one control block.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    StarveilSearchField(
        value = query,
        onValueChange = { next -> if (next.isEmpty()) onClear() else onQueryChange(next) },
        placeholder = stringResource(R.string.profiles_search_hint),
        modifier = Modifier.padding(
            start = StarveilSpacing.pageMargin,
            end = StarveilSpacing.pageMargin,
            top = StarveilSpacing.sm,
        ),
    )
}

@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    /** The tunnel goes through this group, as opposed to the list being filtered to it. */
    inUse: Boolean = false,
) {
    val stateText = when {
        inUse -> stringResource(R.string.profiles_group_state_in_use)
        selected -> stringResource(R.string.profiles_group_state_selected)
        else -> null
    }
    StarveilChip(
        label = label,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        inUse = inUse,
        stateDescription = stateText,
    )
}

/** Tap target padding around the latency badge. */
private val LATENCY_TOUCH_CORNER = 8.dp

/**
 * The latency column: wide enough for "1023 ms" at the inline size and for
 * "timeout" in the mono face, so neither the unit nor the word is ever clipped.
 */
private val LATENCY_COLUMN_WIDTH = 76.dp

/** The FAB's footprint (56dp) plus its margin from the screen edge (16dp). */
private val FAB_GUTTER = 72.dp

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = StarveilType.label,
        color = StarveilColors.ink2,
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

/**
 * One line in a sheet: a title, a tap, and a hairline above it.
 *
 * 56dp tall with the label at headline weight — a menu item is a sentence you
 * read, not a paragraph you parse, and the previous text buttons were neither.
 */
@Composable
private fun StarveilSheetRow(
    text: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Column {
        StarveilDivider(startInset = StarveilSpacing.pageMargin, endInset = StarveilSpacing.pageMargin)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = StarveilSpacing.pageMargin),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = text,
                style = StarveilType.headline,
                color = if (destructive) StarveilColors.errText else StarveilColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One line in the add sheet. */
@Composable
private fun AddSheetRow(title: String, onClick: () -> Unit) {
    StarveilSheetRow(text = title, onClick = onClick)
}

/**
 * The quota the subscription reported, when the provider reports one.
 *
 * A line, not a card: it is a fact about the subscription, not a section. It
 * disappears entirely when the header was missing or unreadable — a "no data"
 * banner would be a permanent apology for something the user cannot act on.
 */
@Composable
private fun UsageBanner(
    info: com.justnpcx.starveil.core.model.SubscriptionUserInfo,
    modifier: Modifier = Modifier,
) {
    val days = info.daysLeft(System.currentTimeMillis() / 1000L)
    val parts = buildList {
        val used = info.usedBytes
        val total = info.totalBytes
        when {
            used != null && total != null ->
                add(stringResource(R.string.profiles_usage_of, formatBytes(used), formatBytes(total)))
            used != null -> add(stringResource(R.string.profiles_usage_used, formatBytes(used)))
            total != null -> add(stringResource(R.string.profiles_usage_total, formatBytes(total)))
        }
        if (days != null) add(stringResource(R.string.profiles_usage_days, days))
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = StarveilType.label,
        color = StarveilColors.ink2,
        modifier = modifier.padding(
            start = StarveilSpacing.pageMargin,
            end = StarveilSpacing.pageMargin,
            top = StarveilSpacing.sm,
        ),
    )
}

/** Bytes in the shortest unit that keeps one decimal. */
private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0 || value >= 100) {
        "${value.toLong()} ${units[unit]}"
    } else {
        String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
    }
}
