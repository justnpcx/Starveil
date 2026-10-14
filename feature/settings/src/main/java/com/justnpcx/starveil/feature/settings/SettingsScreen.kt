package com.justnpcx.starveil.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.LeakCheckRecord
import java.text.DateFormat
import java.util.Date
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * Settings — M2 carries the Kill Switch three-way choice and the minimal leak
 * self-check readout. The rest of the grouped preferences land in M3.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
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

        Text(
            text = stringResource(R.string.kill_switch_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        KillSwitchMode.entries.forEach { mode ->
            KillSwitchOption(
                mode = mode,
                selected = mode == state.killSwitchMode,
                onSelect = { viewModel.setKillSwitchMode(mode) },
            )
        }

        Text(
            text = stringResource(R.string.leak_check_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = leakCheckSummary(state.lastLeakCheck),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KillSwitchOption(
    mode: KillSwitchMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(StarveilSpacing.sm))
        Column {
            Text(text = stringResource(mode.labelRes()), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(mode.descriptionRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            if (record.blocked) R.string.leak_check_leaking else R.string.leak_check_clean,
            when_,
        )
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
