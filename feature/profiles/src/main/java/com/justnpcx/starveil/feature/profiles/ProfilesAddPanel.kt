package com.justnpcx.starveil.feature.profiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import com.justnpcx.starveil.core.subscription.LinkPreview
import com.justnpcx.starveil.core.ui.components.AddActionRow
import com.justnpcx.starveil.core.ui.components.AddPanelLabel
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

/** Which secondary row is open. One at a time: the panel is short. */
private enum class PanelRow { PASTE, SUBSCRIPTION }

/**
 * The add panel's content on the nodes page.
 *
 * Three channels in one row — scanning a code, taking what is on the clipboard,
 * and typing a node by hand are the same size of decision, so they get the same
 * size of control. The two batch flows sit below, because "several nodes at
 * once" is a variation on importing rather than a fourth peer.
 *
 * Nothing here opens a dialog: every item either starts an import or expands
 * inside this sheet.
 */
@Composable
internal fun ProfilesAddPanel(
    /** What the clipboard holds, or null when there is nothing importable. */
    clipboard: LinkPreview?,
    /** Resolves typed text without importing it, for the live count. */
    previewOf: (String) -> LinkPreview,
    /** Set once a subscription has been imported from this panel. */
    subscriptionImport: SubscriptionImported?,
    onScan: () -> Unit,
    onClipboard: () -> Unit,
    onManual: () -> Unit,
    onImportLinks: (String) -> Unit,
    onImportSubscription: (url: String, name: String) -> Unit,
    onViewSubscription: (Long) -> Unit,
) {
    var open by remember { mutableStateOf<PanelRow?>(null) }
    var linkText by remember { mutableStateOf("") }
    var subscriptionUrl by remember { mutableStateOf("") }
    var subscriptionName by remember { mutableStateOf("") }

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

        if (clipboard != null) {
            AddSuggestionCard(
                title = stringResource(R.string.add_clipboard_title, clipboard.nodes.size),
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
            AddTile(
                icon = StarveilIcons.Clipboard,
                title = stringResource(R.string.add_tile_clipboard),
                // A tile that looks live and then reports an error is worse
                // than one that says why it is grey.
                subtitle = if (clipboard == null) {
                    stringResource(R.string.add_tile_clipboard_empty)
                } else {
                    stringResource(R.string.add_tile_clipboard_sub)
                },
                modifier = Modifier.weight(1f),
                enabled = clipboard != null,
                onClick = onClipboard,
            )
            AddTile(
                icon = StarveilIcons.Pencil,
                title = stringResource(R.string.add_tile_manual),
                subtitle = stringResource(R.string.add_tile_manual_sub),
                modifier = Modifier.weight(1f),
                onClick = onManual,
            )
        }

        AddPanelLabel(stringResource(R.string.add_panel_label_batch))

        AddActionRow(
            icon = StarveilIcons.Link,
            title = stringResource(R.string.add_row_paste),
            subtitle = stringResource(R.string.add_row_paste_sub),
            expanded = open == PanelRow.PASTE,
            onClick = { open = if (open == PanelRow.PASTE) null else PanelRow.PASTE },
        )
        AnimatedVisibility(visible = open == PanelRow.PASTE) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = StarveilSpacing.md),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
            ) {
                StarveilTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    label = stringResource(R.string.add_link_hint),
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val detected = remember(linkText) { previewOf(linkText).nodes.size }
                    Text(
                        text = if (detected > 0) {
                            stringResource(R.string.add_detected, detected)
                        } else {
                            ""
                        },
                        style = StarveilType.label,
                        color = StarveilColors.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    StarveilButton(
                        text = stringResource(R.string.add_action_import),
                        enabled = linkText.isNotBlank(),
                        onClick = { onImportLinks(linkText) },
                    )
                }
            }
        }

        AddActionRow(
            icon = StarveilIcons.Down,
            title = stringResource(R.string.add_row_subscription),
            subtitle = stringResource(R.string.add_row_subscription_sub),
            expanded = open == PanelRow.SUBSCRIPTION,
            onClick = {
                open = if (open == PanelRow.SUBSCRIPTION) null else PanelRow.SUBSCRIPTION
            },
        )
        AnimatedVisibility(visible = open == PanelRow.SUBSCRIPTION) {
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
}

/** "host:port · vless" for the clipboard card's second line. */
private fun LinkPreview.describe(): String {
    val node = nodes.firstOrNull() ?: return subscriptionUrl.orEmpty()
    val head = node.server + ":" + node.port
    return if (nodes.size > 1) head + " …" else head + " · " + node.type.name.lowercase()
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

