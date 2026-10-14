package com.justnpcx.starveil.feature.rules

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
 * Rules — routing rules and remote rule sets.
 *
 * Shows an honest empty state until rules can be created (the add flow is the
 * next step for this screen); no placeholder copy ships.
 */
@Composable
fun RulesScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(R.string.rules_title), style = MaterialTheme.typography.titleLarge)
        Text(text = stringResource(R.string.rules_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.rules_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
