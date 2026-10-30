package com.justnpcx.starveil.feature.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.justnpcx.starveil.core.ui.components.AddTile
import com.justnpcx.starveil.core.ui.components.AddTileRow
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * The add panel's content on the nodes page.
 *
 * Three channels, one row. They are peers — scanning a code, taking what is on
 * the clipboard, and typing a node by hand are the same size of decision — so
 * they get the same size of control. The subscription and paste-a-link flows
 * live below them because they are variations on "bring in a batch", not a
 * fourth peer.
 *
 * Nothing here opens a dialog: every item either starts an import or expands
 * inside this sheet.
 */
@Composable
internal fun ProfilesAddPanel(
    onScan: () -> Unit,
    onClipboard: () -> Unit,
    onManual: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.add_panel_title_node),
            style = StarveilType.title,
            color = StarveilColors.ink,
            modifier = Modifier.padding(bottom = StarveilSpacing.md),
        )
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
                subtitle = stringResource(R.string.add_tile_clipboard_sub),
                modifier = Modifier.weight(1f),
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
    }
}
