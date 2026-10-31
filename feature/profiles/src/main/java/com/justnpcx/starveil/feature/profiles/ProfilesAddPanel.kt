package com.justnpcx.starveil.feature.profiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import com.justnpcx.starveil.core.subscription.LinkPreview
import com.justnpcx.starveil.core.ui.components.AddActionRow
import com.justnpcx.starveil.core.ui.components.AddResultLine
import com.justnpcx.starveil.core.ui.components.AddSuggestionCard
import com.justnpcx.starveil.core.ui.components.AddTile
import com.justnpcx.starveil.core.ui.components.AddTileRow
import com.justnpcx.starveil.core.ui.components.StarveilButton
import com.justnpcx.starveil.core.ui.components.StarveilIconAction
import com.justnpcx.starveil.core.ui.components.StarveilTextField
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilShapes
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType
import com.justnpcx.starveil.core.ui.util.readClipboardText

/**
 * The add panel's content on the nodes page.
 *
 * Three channels in one row — scanning a code, taking what is on the clipboard,
 * and typing a node by hand are the same size of decision, so they get the same
 * size of control. A subscription sits below the line, because "several nodes
 * at once" is a variation on importing rather than a fourth peer.
 *
 * One door per path. When the clipboard already holds something importable,
 * the card at the top is the import and the tile that leads to the same import
 * is gone; when it does not, the card has nothing to offer and the tile is the
 * only way in, which is the form for typing or pasting a link.
 */
@Composable
internal fun ProfilesAddPanel(
    /** What the clipboard holds, or null when there is nothing importable. */
    clipboard: LinkPreview?,
    /** Resolves typed text without importing it, for the live count. */
    previewOf: (String) -> LinkPreview,
    /** Set once a subscription has been imported from this panel. */
    subscriptionImport: SubscriptionImported?,
    /** Opened from the empty state's "import a subscription". */
    startWithSubscription: Boolean = false,
    onScan: () -> Unit,
    /** The clipboard card's one-tap import: no form, no confirmation. */
    onClipboard: () -> Unit,
    onManual: () -> Unit,
    onImportLinks: (String) -> Unit,
    onImportSubscription: (url: String, name: String) -> Unit,
    onViewSubscription: (Long) -> Unit,
) {
    var subscriptionOpen by remember { mutableStateOf(startWithSubscription) }
    var clipboardForm by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var subscriptionUrl by remember { mutableStateOf("") }
    var subscriptionName by remember { mutableStateOf("") }

    LaunchedEffect(startWithSubscription) {
        if (startWithSubscription) subscriptionOpen = true
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.add_panel_title_node),
            style = StarveilType.title,
            color = StarveilColors.ink,
            modifier = Modifier.padding(bottom = StarveilSpacing.md),
        )

        // A subscription import finishes here: the panel stays open and says
        // what arrived, with the one action that matters next.
        if (subscriptionImport != null) {
            AddResultLine(
                text = stringResource(
                    R.string.add_subscription_done,
                    subscriptionImport.name,
                    subscriptionImport.count,
                ),
                action = stringResource(R.string.add_view),
                onAction = { onViewSubscription(subscriptionImport.groupId) },
            )
            Spacer(modifier = Modifier.height(StarveilSpacing.md))
        }

        // The card and the tile are the same door, so only one of them is ever
        // on screen: with something importable on the clipboard the card is the
        // shorter path and the tile would be a second way to do the same thing;
        // with nothing there, the card has nothing to say and the tile is the
        // way to type or paste a link by hand.
        if (clipboard != null) {
            AddSuggestionCard(
                title = if (clipboard.subscriptionUrl != null) {
                    stringResource(R.string.add_clipboard_subscription_title)
                } else {
                    stringResource(R.string.add_clipboard_title, clipboard.nodes.size)
                },
                detail = clipboard.describe(),
                action = stringResource(R.string.add_action_import),
                onAction = onClipboard,
            )
            Spacer(modifier = Modifier.height(StarveilSpacing.md))
        }

        AddTileRow {
            AddTile(
                icon = StarveilIcons.Scan,
                title = stringResource(R.string.add_tile_scan),
                subtitle = stringResource(R.string.add_tile_scan_sub),
                modifier = Modifier.weight(1f),
                onClick = onScan,
            )
            if (clipboard == null) {
                AddTile(
                    icon = StarveilIcons.Clipboard,
                    title = stringResource(R.string.add_tile_clipboard),
                    subtitle = stringResource(R.string.add_tile_clipboard_sub),
                    modifier = Modifier.weight(1f),
                    onClick = { clipboardForm = true },
                )
            }
            AddTile(
                icon = StarveilIcons.Pencil,
                title = stringResource(R.string.add_tile_manual),
                subtitle = stringResource(R.string.add_tile_manual_sub),
                modifier = Modifier.weight(1f),
                onClick = onManual,
            )
        }

        // One row, one chevron: a subscription is not a fourth peer of the
        // three tiles — it is a link you paste, and it stays below the line.
        AddActionRow(
            icon = StarveilIcons.Link,
            title = stringResource(R.string.add_row_subscription),
            subtitle = stringResource(R.string.add_row_subscription_sub),
            expanded = subscriptionOpen,
            onClick = { subscriptionOpen = !subscriptionOpen },
        )
        AnimatedVisibility(visible = subscriptionOpen) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = StarveilSpacing.md),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
            ) {
                StarveilTextField(
                    value = subscriptionUrl,
                    onValueChange = { subscriptionUrl = it },
                    label = stringResource(R.string.add_row_subscription_url),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                StarveilTextField(
                    value = subscriptionName,
                    onValueChange = { subscriptionName = it },
                    label = stringResource(R.string.add_row_subscription_name),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    StarveilButton(
                        text = stringResource(R.string.add_action_import),
                        enabled = subscriptionUrl.isNotBlank(),
                        onClick = { onImportSubscription(subscriptionUrl, subscriptionName) },
                    )
                }
            }
        }
    }

    if (clipboardForm) {
        ClipboardImportDialog(
            previewOf = previewOf,
            onDismiss = { clipboardForm = false },
            onImport = { text ->
                clipboardForm = false
                onImportLinks(text)
            },
        )
    }
}

/**
 * The clipboard tile's form.
 *
 * A dialog rather than another expansion inside the sheet: the tile is only on
 * screen when the clipboard has nothing to offer, and what it opens is a
 * text area with its own keyboard — the one thing the sheet has no room for.
 * "Paste" reads the clipboard on the tap, which is also the moment Android
 * will hand it over.
 */
@Composable
private fun ClipboardImportDialog(
    previewOf: (String) -> LinkPreview,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val detected = remember(text) { previewOf(text).nodes.size }
    AlertDialog(
        containerColor = StarveilColors.card,
        shape = StarveilShapes.dialog,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.add_clipboard_form_title),
                style = StarveilType.headline,
                color = StarveilColors.ink,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                StarveilTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.add_link_hint),
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.add_clipboard_form_formats),
                        style = StarveilType.label,
                        color = StarveilColors.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            // Empty clipboard is not an error: the field simply
                            // stays as it was.
                            readClipboardText(context)?.let { text = it }
                        },
                    ) {
                        Text(stringResource(R.string.add_clipboard_form_paste))
                    }
                }
                if (detected > 0) {
                    Text(
                        text = stringResource(R.string.add_detected, detected),
                        style = StarveilType.label,
                        color = StarveilColors.accentText,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onImport(text) },
            ) {
                Text(stringResource(R.string.add_action_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.add_cancel))
            }
        },
    )
}
/**
 * The card's second line: the protocol, a host fragment and the port.
 *
 * Deliberately not the link. A share link carries the node's credentials in its
 * user-info or its query, and a card that shows them puts a secret on screen to
 * be screenshotted; what identifies the node for the person importing it is the
 * protocol, the host and the port.
 */
private fun LinkPreview.describe(): String {
    val node = nodes.firstOrNull()
    if (node == null) {
        // A subscription is identified by where it comes from, not by its path
        // or token: the host is enough to recognise it.
        val host = subscriptionUrl?.let {
            runCatching { java.net.URI(it).host }.getOrNull()
        }
        return host ?: middleEllipsis(subscriptionUrl.orEmpty(), max = 24)
    }
    val head = node.type.name.lowercase() + " · " + middleEllipsis(node.server, max = 22) +
        ":" + node.port
    return if (nodes.size > 1) head + " …" else head
}

/**
 * The in-page clipboard bar.
 *
 * A bar in the page, not a snackbar: the snackbar is where the FAB and the
 * gesture area live, so a suggestion there is both transient and in the way.
 * The bar is dismissed by ×, and by itself once the import succeeds.
 */
@Composable
internal fun ClipboardBar(
    suggestion: ClipboardSuggestion,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = StarveilSpacing.pageMargin, vertical = StarveilSpacing.sm)
            .clip(StarveilShapes.card)
            .background(StarveilColors.selBg)
            .padding(start = StarveilSpacing.md, end = StarveilSpacing.sm, top = StarveilSpacing.sm, bottom = StarveilSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = StarveilIcons.Clipboard,
            contentDescription = null,
            tint = StarveilColors.selInk,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(StarveilSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.add_clipboard_bar_title),
                style = StarveilType.body,
                color = StarveilColors.selInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = suggestion.detail,
                style = StarveilType.label,
                color = StarveilColors.selInk.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StarveilButton(text = stringResource(R.string.add_action_import), onClick = onImport, secondary = true)
        StarveilIconAction(
            icon = StarveilIcons.Close,
            contentDescription = stringResource(R.string.add_clipboard_bar_dismiss),
            onClick = onDismiss,
        )
    }
}


/**
 * The nodes page with nothing on it.
 *
 * Centred, with the brand star drawn as an outline: an empty list is a state,
 * not an error, and the two ways out are both here rather than one of them
 * being a button in a corner.
 */
@Composable
internal fun NodesEmptyState(onAdd: () -> Unit, onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(StarveilSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = StarveilIcons.StarOutline,
            contentDescription = null,
            tint = StarveilColors.accent.copy(alpha = 0.55f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(StarveilSpacing.lg))
        Text(
            text = stringResource(R.string.profiles_empty_title),
            style = StarveilType.title,
            color = StarveilColors.ink,
        )
        Spacer(modifier = Modifier.height(StarveilSpacing.xs))
        Text(
            text = stringResource(R.string.profiles_empty_body),
            style = StarveilType.label,
            color = StarveilColors.ink2,
        )
        Spacer(modifier = Modifier.height(StarveilSpacing.xl))
        StarveilButton(text = stringResource(R.string.add_panel_title_node), onClick = onAdd)
        Spacer(modifier = Modifier.height(StarveilSpacing.sm))
        StarveilButton(
            text = stringResource(R.string.add_row_subscription),
            secondary = true,
            onClick = onImport,
        )
    }
}
