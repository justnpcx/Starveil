package com.justnpcx.starveil.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * The bundle, before it goes anywhere.
 *
 * A preview rather than a share sheet straight from the row: the user is about
 * to publish their own network's numbers, and the only honest way to ask is to
 * show them the text first. Nothing is written to a file, and nothing is
 * uploaded — the button copies.
 *
 * Public because the row that opens it lives on the hub, which is outside this
 * module; the wording and the preview itself stay here with the bundle's own
 * strings.
 */
@Composable
fun DiagnosticPreviewDialog(
    text: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diagnostics_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                Text(
                    text = stringResource(R.string.diagnostics_preview_note),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(text = text, style = StarveilType.log, color = StarveilColors.ink2)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) {
                Text(stringResource(R.string.diagnostics_preview_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.diagnostics_preview_cancel))
            }
        },
    )
}
