package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.justnpcx.starveil.core.ui.R
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * The one way this app tells the user something in place.
 *
 * Two screens had each grown their own: the Nodes screen a banner, the
 * Dashboard a bare line of error-coloured text. Same job, two presentations,
 * so a change to one would silently miss the other (K3, C5).
 *
 * [onDismiss] is null for a message that explains a state rather than reporting
 * an event — the Dashboard's "why the tunnel is blocked" must not be
 * dismissible, because dismissing it would leave a blocked core with no reason
 * on screen. An event ("Imported 2 nodes") can go away.
 */
@Composable
fun MessageBanner(
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
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
            onDismiss?.let { dismiss ->
                TextButton(onClick = dismiss) {
                    Text(stringResource(R.string.core_message_dismiss))
                }
            }
        }
    }
}
