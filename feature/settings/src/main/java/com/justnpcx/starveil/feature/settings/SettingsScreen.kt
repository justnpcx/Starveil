package com.justnpcx.starveil.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings — grouped preferences (basic / tuning / privacy / advanced).
 * TODO (Phase 3): DataStore-backed preferences; protocol tuning switches
 * (mux, Hysteria2, 0-RTT) arrive with Phase 2 kernel work.
 */
@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "待实现：分组设置 / DataStore 偏好（Phase 3）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
