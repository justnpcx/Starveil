package com.justnpcx.starveil.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.ui.components.StarveilTextField
import com.justnpcx.starveil.core.model.AppRoute
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * Choose where each app's traffic goes.
 *
 * Only apps that deviate are listed as configured; the tunnel is the default.
 * The explainer says when a change takes effect, because it does: the target
 * becomes a `package_name` route rule, and those are built when the tunnel is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppScreen(
    viewModel: PerAppViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // No bar of its own: the shell draws the title and the arrow back.
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = stringResource(R.string.per_app_explainer),
                style = StarveilType.label,
                color = StarveilColors.ink2,
                modifier = Modifier.padding(
                    start = StarveilSpacing.lg,
                    end = StarveilSpacing.lg,
                    top = StarveilSpacing.sm,
                    bottom = StarveilSpacing.sm,
                ),
            )
            StarveilTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = stringResource(R.string.per_app_search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = StarveilSpacing.lg),
            )

            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.visible.isEmpty() -> Text(
                    text = stringResource(R.string.per_app_empty),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                    modifier = Modifier.padding(StarveilSpacing.lg),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = StarveilSpacing.navContentInset),
                ) {
                    items(state.visible, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            target = targetLabel(state.routes[app.packageName], state.nodes),
                            onEdit = { viewModel.edit(app.packageName) },
                        )
                    }
                }
            }
        }
    }

    val editing = state.editing
    if (editing != null) {
        TargetDialog(
            title = state.apps.firstOrNull { it.packageName == editing }?.label ?: editing,
            selected = state.routes[editing],
            nodes = state.nodes,
            onSelect = { viewModel.setRoute(editing, it) },
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    target: String,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit, role = Role.Button)
            .padding(horizontal = StarveilSpacing.lg, vertical = StarveilSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = StarveilType.body,
                color = StarveilColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = StarveilType.label,
                color = StarveilColors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = target,
            style = StarveilType.label,
            color = StarveilColors.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The route picker.
 *
 * "Tunnel default" is first and is the absence of an entry, not a third kind of
 * value — that keeps the stored map meaning exactly what it says.
 */
@Composable
private fun TargetDialog(
    title: String,
    selected: AppRoute?,
    nodes: List<Profile>,
    onSelect: (AppRoute?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TargetOption(
                    label = stringResource(R.string.per_app_target_default),
                    selected = selected == null,
                    onSelect = { onSelect(null) },
                )
                TargetOption(
                    label = stringResource(R.string.per_app_target_direct),
                    selected = selected == AppRoute.Direct,
                    onSelect = { onSelect(AppRoute.Direct) },
                )
                nodes.forEach { node ->
                    TargetOption(
                        label = node.name,
                        selected = selected == AppRoute.Node(node.id),
                        onSelect = { onSelect(AppRoute.Node(node.id)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.per_app_close))
            }
        },
    )
}

@Composable
private fun TargetOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = StarveilSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = StarveilColors.accent,
                unselectedColor = StarveilColors.ink2,
            ),
        )
        Text(
            text = label,
            style = StarveilType.body,
            color = StarveilColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun targetLabel(route: AppRoute?, nodes: List<Profile>): String = when (route) {
    null -> stringResource(R.string.per_app_target_default)
    AppRoute.Direct -> stringResource(R.string.per_app_target_direct)
    is AppRoute.Node -> nodes.firstOrNull { it.id == route.profileId }?.name
        ?: stringResource(R.string.per_app_target_missing)
}
