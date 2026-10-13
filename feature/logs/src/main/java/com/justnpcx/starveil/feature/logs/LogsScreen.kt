package com.justnpcx.starveil.feature.logs

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
 * Logs — kernel + app logs, diagnosis.
 * TODO (Phase 3): LazyColumn log viewer, level filter, keyword search,
 * redacted export (see README "安全红线": logs must never carry credentials).
 */
@Composable
fun LogsScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Logs", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "待实现：日志查看器 / 级别筛选 / 搜索 / 脱敏导出（Phase 3）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
