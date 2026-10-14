package com.justnpcx.starveil.feature.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Logs — kernel + app logs, diagnosis.
 *
 * The viewer (level filter, search, redacted export) needs the kernel's log
 * flow, which :feature:* must not touch; it lands with a narrow port, like the
 * consent one. Until then this is an honest empty state, not a placeholder.
 */
@Composable
fun LogsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(R.string.logs_title), style = MaterialTheme.typography.titleLarge)
        Text(text = stringResource(R.string.logs_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.logs_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
