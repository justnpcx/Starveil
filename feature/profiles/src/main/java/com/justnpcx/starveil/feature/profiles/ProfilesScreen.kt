package com.justnpcx.starveil.feature.profiles

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
 * Nodes — groups, subscriptions, node list.
 * TODO (Phase 3): group tabs + node rows with health sparklines + sort/filter.
 */
@Composable
fun ProfilesScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Nodes", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "待实现：分组 / 订阅 / 节点列表（Phase 3）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
