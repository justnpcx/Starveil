package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * The one empty state.
 *
 * Every list screen needs the same thing — say what is missing, then what to do
 * about it — and three screens had each written their own copy of it. Same
 * shape three times, so a fix to one silently missed the other two (K3).
 *
 * [body] is not optional: "No nodes yet" on its own tells the user nothing
 * about what to do next, which is the entire job of an empty state (C5).
 *
 * `fillMaxWidth` rather than `fillMaxSize`, so it is correct both as a screen's
 * whole content and as an item inside a `LazyColumn` — the two places this is
 * used. A caller that wants it to fill the screen can pass the modifier.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(StarveilSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
