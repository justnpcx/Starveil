package com.justnpcx.starveil.feature.rules

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
 * Rules — routing rules and remote rule sets.
 * TODO (Phase 3): rule flow visualization (matcher → action), live hit highlight.
 */
@Composable
fun RulesScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Rules", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "待实现：路由规则 / 远程规则集 / 流向可视化（Phase 3）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
