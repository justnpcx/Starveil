package com.justnpcx.starveil.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.LeakCheckRecord
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    /** Opens the per-app exception list. :app owns the route. */
    onOpenPerApp: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()

    var askingForExport by remember { mutableStateOf(false) }
    /** Held between the passphrase dialog and the file picker's result. */
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val passphrase = exportPassphrase
        exportPassphrase = null
        if (uri != null && passphrase != null) viewModel.export(uri, passphrase)
    }
    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // The passphrase is asked for after the file, so a mis-tap on the picker
        // does not leave a typed secret sitting in a field.
        if (uri != null) restoreUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Without this the screen clips: on a 1080x2400 display the leak
            // self-check and the kernel version sit below the fold with no way
            // to reach them. Found on device — the version row simply was not
            // there, and the fix is not in the version code.
            .verticalScroll(rememberScrollState())
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
        SwitchRow(
            title = stringResource(R.string.auto_switch_title),
            description = stringResource(R.string.auto_switch_desc),
            checked = state.autoSwitchOnDrop,
            onCheckedChange = viewModel::setAutoSwitchOnDrop,
        )

        SectionTitle(stringResource(R.string.per_app_entry_title))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (state.perAppDirectCount == 0) {
                    stringResource(R.string.per_app_entry_desc)
                } else {
                    stringResource(R.string.per_app_entry_count, state.perAppDirectCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenPerApp) {
                Text(stringResource(R.string.per_app_manage))
            }
        }

        SectionTitle(stringResource(R.string.backup_title))
        Text(
            text = stringResource(R.string.backup_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
            TextButton(onClick = { askingForExport = true }) {
                Text(stringResource(R.string.backup_export))
            }
            TextButton(
                onClick = {
                    openBackup.launch(arrayOf("application/octet-stream", "*/*"))
                },
            ) {
                Text(stringResource(R.string.backup_restore))
            }
        }

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

        if (askingForExport) {
            PassphraseDialog(
                title = stringResource(R.string.backup_export_title),
                onDismiss = { askingForExport = false },
                onConfirm = { passphrase ->
                    askingForExport = false
                    exportPassphrase = passphrase
                    createBackup.launch(defaultBackupFileName())
                },
            )
        }

        restoreUri?.let { uri ->
            PassphraseDialog(
                title = stringResource(R.string.backup_restore_title),
                onDismiss = { restoreUri = null },
                onConfirm = { passphrase ->
                    restoreUri = null
                    viewModel.restore(uri, passphrase)
                },
            )
        }

        backupMessage?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::clearBackupMessage,
                title = { Text(stringResource(R.string.backup_result_title)) },
                text = { Text(message.text()) },
                confirmButton = {
                    TextButton(onClick = viewModel::clearBackupMessage) {
                        Text(stringResource(R.string.per_app_close))
                    }
                },
            )
        }
    }
}

/**
 * The passphrase prompt, shared by export and restore.
 *
 * The field is a `String` because that is what a TextField holds; the
 * `CharArray` handed to the caller is wiped once it has been used. The
 * minimum length is not policy theatre: a one-character passphrase on a file
 * that contains every credential is the same as no passphrase at all.
 */
@Composable
private fun PassphraseDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.backup_passphrase_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.backup_passphrase)) },
                    supportingText = {
                        Text(stringResource(R.string.backup_passphrase_min, MIN_PASSPHRASE))
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.length >= MIN_PASSPHRASE,
                onClick = { onConfirm(value.toCharArray()) },
            ) {
                Text(stringResource(R.string.backup_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.per_app_close))
            }
        },
    )
}

@Composable
private fun BackupMessage.text(): String = when (this) {
    is BackupMessage.Exported -> stringResource(R.string.backup_exported, bytes)
    is BackupMessage.Restored ->
        stringResource(R.string.backup_restored, profiles, groups, rules)
    BackupMessage.WrongPassphrase -> stringResource(R.string.backup_wrong_passphrase)
    is BackupMessage.Failed -> stringResource(R.string.backup_failed, reason)
}

/** Enough entropy that guessing is not the cheapest attack on the file. */
private const val MIN_PASSPHRASE = 8

private fun defaultBackupFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "starveil-$stamp.svb"
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
