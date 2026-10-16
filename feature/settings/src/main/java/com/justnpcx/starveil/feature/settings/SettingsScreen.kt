package com.justnpcx.starveil.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.LeakCheckRecord
import java.text.DateFormat
import java.util.Date
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * Settings — theme, the Kill Switch three-way choice, and the leak self-check.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    /**
     * Runs the leak self-check. Supplied by :app, which owns the VpnService the
     * check inspects; the default keeps previews and tests platform-free.
     */
    onRunLeakCheck: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(StarveilSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
        )

        SectionTitle(stringResource(R.string.theme_title))
        ThemeMode.entries.forEach { mode ->
            RadioOption(
                label = stringResource(mode.labelRes()),
                description = null,
                selected = mode == state.themeMode,
                onSelect = { viewModel.setThemeMode(mode) },
            )
        }
        SwitchRow(
            title = stringResource(R.string.dynamic_color_title),
            description = stringResource(R.string.dynamic_color_desc),
            checked = state.dynamicColor,
            onCheckedChange = viewModel::setDynamicColor,
        )

        SectionTitle(stringResource(R.string.kill_switch_title))
        KillSwitchMode.entries.forEach { mode ->
            RadioOption(
                label = stringResource(mode.labelRes()),
                description = stringResource(mode.descriptionRes()),
                selected = mode == state.killSwitchMode,
                onSelect = { viewModel.setKillSwitchMode(mode) },
            )
        }
        SwitchRow(
            title = stringResource(R.string.auto_reconnect_title),
            description = stringResource(R.string.auto_reconnect_desc),
            checked = state.autoReconnect,
            onCheckedChange = viewModel::setAutoReconnect,
        )

        SectionTitle(stringResource(R.string.leak_check_title))
        Text(
            text = leakCheckSummary(state.lastLeakCheck),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRunLeakCheck) {
            Text(stringResource(R.string.leak_check_run))
        }

        SectionTitle(stringResource(R.string.settings_kernel_title))
        Text(
            // The core names itself; this is the version a bug report should
            // quote, and it is read from the shipped library rather than
            // written down here where it would drift.
            text = state.kernelVersion.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.settings_kernel_version, it) }
                ?: stringResource(R.string.settings_kernel_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = StarveilSpacing.sm),
    )
}

@Composable
private fun RadioOption(
    label: String,
    description: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = StarveilSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(StarveilSpacing.sm))
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = StarveilSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(StarveilSpacing.sm))
        // onCheckedChange = null: the whole row is the target, so the switch
        // must not also be one (it would toggle twice).
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun leakCheckSummary(record: LeakCheckRecord): String =
    if (record.checkedAtEpochMs <= 0L) {
        stringResource(R.string.leak_check_never)
    } else {
        val when_ = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(record.checkedAtEpochMs))
        stringResource(
            if (record.leaking) R.string.leak_check_leaking else R.string.leak_check_clean,
            when_,
        )
    }

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.LIGHT -> R.string.theme_light
}

private fun KillSwitchMode.labelRes(): Int = when (this) {
    KillSwitchMode.OFF -> R.string.kill_switch_off
    KillSwitchMode.BLOCK_ON_DISCONNECT -> R.string.kill_switch_on_disconnect
    KillSwitchMode.ALWAYS_BLOCK -> R.string.kill_switch_always
}

private fun KillSwitchMode.descriptionRes(): Int = when (this) {
    KillSwitchMode.OFF -> R.string.kill_switch_off_desc
    KillSwitchMode.BLOCK_ON_DISCONNECT -> R.string.kill_switch_on_disconnect_desc
    KillSwitchMode.ALWAYS_BLOCK -> R.string.kill_switch_always_desc
}
