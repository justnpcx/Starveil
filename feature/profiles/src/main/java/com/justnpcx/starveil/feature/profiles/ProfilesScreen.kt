package com.justnpcx.starveil.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * Nodes — the real list plus the import entry point (paste a share link or a
 * subscription URL). Tapping a node selects it, which the Dashboard and the
 * config builder read.
 */
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel = hiltViewModel(),
    /**
     * Measures the selected node's latency. Supplied by :app, which owns the
     * kernel; the default keeps previews and tests kernel-free.
     */
    onTestLatency: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showImport by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            state.message?.let { message ->
                MessageBanner(message = message, onDismiss = viewModel::dismissMessage)
            }
            if (state.profiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onTestLatency) {
                        Text(stringResource(R.string.profiles_test_latency))
                    }
                }
            }
            if (state.profiles.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
                ) {
                    items(state.profiles, key = { it.id }) { profile ->
                        NodeRow(
                            profile = profile,
                            selected = profile.id == state.selectedId,
                            onClick = { viewModel.select(profile.id) },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showImport = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(StarveilSpacing.lg),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.profiles_import_add))
        }
    }

    if (showImport) {
        ImportDialog(
            importing = state.importing,
            onDismiss = { showImport = false },
            onImport = { text ->
                showImport = false
                viewModel.import(text)
            },
        )
    }
}

@Composable
private fun ImportDialog(
    importing: Boolean,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles_import_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                minLines = 3,
                label = { Text(stringResource(R.string.profiles_import_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(text) },
                enabled = !importing && text.isNotBlank(),
            ) {
                Text(
                    stringResource(
                        if (importing) R.string.profiles_import_working else R.string.profiles_import_action,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_import_cancel)) }
        },
    )
}

@Composable
private fun MessageBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_message_dismiss)) }
        }
    }
}

@Composable
private fun NodeRow(
    profile: Profile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(StarveilSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${profile.type.name.lowercase()} · ${profile.server}:${profile.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (profile.latencyMs >= 0) {
                    stringResource(R.string.profiles_latency_ms, profile.latencyMs)
                } else {
                    stringResource(R.string.profiles_latency_untested)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(StarveilSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        Text(text = stringResource(R.string.profiles_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.profiles_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
