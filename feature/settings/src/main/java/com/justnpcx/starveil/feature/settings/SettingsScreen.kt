package com.justnpcx.starveil.feature.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.LeakCheckRecord
import com.justnpcx.starveil.core.model.LocalApi
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.justnpcx.starveil.core.data.update.UpdateInstaller
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
    onOpenKeepAlive: () -> Unit = {},
    /** Opens the privacy policy. :app owns the route. */
    onOpenPrivacy: () -> Unit = {},
    /**
     * Whether this build has a Sentry DSN. A switch that cannot do anything is
     * worse than no switch, so the row is hidden rather than disabled.
     */
    crashReportingAvailable: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val updateState by viewModel.update.collectAsStateWithLifecycle()

    // The version a beta user should quote in a bug report. Read from the
    // installed package rather than duplicated in a string, which is the same
    // reason the kernel version is read from the library.
    val context = LocalContext.current
    val appVersion = remember(context) {
        runCatching {
            val manager = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                manager.getPackageInfo(context.packageName, 0)
            }
            info.versionName
        }.getOrNull()
    }

    var askingForExport by remember { mutableStateOf(false) }
    /** Held between the passphrase dialog and the file picker's result. */
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var confirmingTokenReset by remember { mutableStateOf(false) }

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

    LaunchedEffect(updateState) {
        val ready = updateState as? UpdateUiState.Ready ?: return@LaunchedEffect
        if (UpdateInstaller.canInstall(context)) {
            context.startActivity(UpdateInstaller.intent(context, ready.file))
            viewModel.clearUpdate()
        }
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
        // Two locks, and they are not the same lock. The modes above are a
        // config this app enforces, so they end when the process does; the
        // system's always-on VPN survives a crash and a swipe-away. Linking
        // rather than duplicating the switch is deliberate — only the system
        // can turn its own lock on, and pretending otherwise would be a lie.
        TextButton(
            onClick = { openSystemVpnSettings(context) },
            contentPadding = PaddingValues(horizontal = StarveilSpacing.md),
        ) {
            Text(stringResource(R.string.kill_switch_system_link))
        }
        Text(
            text = stringResource(R.string.kill_switch_system_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = StarveilSpacing.md),
        )
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

        SectionTitle(stringResource(R.string.keepalive_entry_title))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.keepalive_entry_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenKeepAlive) {
                Text(stringResource(R.string.keepalive_action_open))
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
            // The core names itself as `1.14.3-<commit>`; the commit is a build
            // detail that changes on every rebuild, and this row is a version,
            // not a build fingerprint. The full string stays in the state for
            // anything that needs to correlate a build.
            text = state.kernelVersion.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.settings_kernel_version, it.substringBefore('-')) }
                ?: stringResource(R.string.settings_kernel_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (crashReportingAvailable) {
            SectionTitle(stringResource(R.string.diagnostics_title))
            SwitchRow(
                title = stringResource(R.string.crash_reports_title),
                description = stringResource(R.string.crash_reports_desc),
                checked = state.crashReports,
                onCheckedChange = viewModel::setCrashReports,
            )
        }

        // Developer options. Not hidden behind a secret gesture: the switch is
        // off by default, and the person who wants it is the person who needs
        // to find it.
        SectionTitle(stringResource(R.string.settings_developer_title))
        SwitchRow(
            title = stringResource(R.string.settings_local_api_title),
            description = stringResource(R.string.settings_local_api_desc, LocalApi.address),
            checked = state.localApiEnabled,
            onCheckedChange = viewModel::setLocalApiEnabled,
        )
        if (state.localApiEnabled) {
            Text(
                text = stringResource(R.string.settings_local_api_address, LocalApi.address),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.apiToken,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                TextButton(
                    enabled = state.apiToken.isNotEmpty(),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Starveil local API token", state.apiToken),
                        )
                    },
                ) {
                    Text(stringResource(R.string.settings_local_api_copy))
                }
                TextButton(
                    enabled = state.apiToken.isNotEmpty(),
                    onClick = { confirmingTokenReset = true },
                ) {
                    Text(stringResource(R.string.settings_local_api_reset))
                }
            }
        }

        SectionTitle(stringResource(R.string.settings_about_title))
        Text(
            text = stringResource(R.string.settings_app_version, appVersion ?: "—"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
            TextButton(
                onClick = { viewModel.checkForUpdates(appVersion.orEmpty()) },
                enabled = updateState !is UpdateUiState.Checking &&
                    updateState !is UpdateUiState.Downloading,
            ) {
                Text(
                    stringResource(
                        if (updateState is UpdateUiState.Checking) {
                            R.string.update_checking
                        } else {
                            R.string.update_check
                        },
                    ),
                )
            }
            TextButton(onClick = onOpenPrivacy) {
                Text(stringResource(R.string.settings_privacy))
            }
        }

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

        UpdateDialog(
            state = updateState,
            onDismiss = viewModel::clearUpdate,
            onDownload = viewModel::downloadUpdate,
            onOpenInstallSettings = {
                context.startActivity(UpdateInstaller.permissionSettings(context))
            },
        )

        if (confirmingTokenReset) {
            AlertDialog(
                onDismissRequest = { confirmingTokenReset = false },
                title = { Text(stringResource(R.string.settings_local_api_reset_title)) },
                text = { Text(stringResource(R.string.settings_local_api_reset_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.resetApiToken()
                            confirmingTokenReset = false
                        },
                    ) {
                        Text(stringResource(R.string.settings_local_api_reset))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmingTokenReset = false }) {
                        Text(stringResource(R.string.settings_cancel))
                    }
                },
            )
        }
    }
}

/**
 * One dialog for every step of the update flow.
 *
 * The alternative — a dialog per state — puts the wording for "checking" and
 * "downloading" in two places and lets them drift. The state decides the
 * buttons as well as the text: nothing to confirm while it is working, and
 * "install" only once the file has been verified.
 */
@Composable
private fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: (com.justnpcx.starveil.core.data.update.UpdateInfo) -> Unit,
    onOpenInstallSettings: () -> Unit,
) {
    val context = LocalContext.current
    when (state) {
        UpdateUiState.Idle -> Unit

        UpdateUiState.Checking -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_check)) },
            text = { Text(stringResource(R.string.update_checking)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.per_app_close)) }
            },
        )

        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_available_title, state.info.version)) },
            text = {
                Text(
                    text = state.info.notes.ifBlank { stringResource(R.string.update_notes_empty) },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { onDownload(state.info) }) {
                    Text(stringResource(R.string.update_download))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
            },
        )

        UpdateUiState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_check)) },
            text = { Text(stringResource(R.string.update_up_to_date)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.per_app_close)) }
            },
        )

        is UpdateUiState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_check)) },
            text = { Text(stringResource(R.string.update_failed, state.reason)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.per_app_close)) }
            },
        )

        is UpdateUiState.DownloadFailed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_check)) },
            text = { Text(stringResource(R.string.update_download_failed, state.reason)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.per_app_close)) }
            },
        )

        UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading)) },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )

        is UpdateUiState.Ready -> if (!UpdateInstaller.canInstall(context)) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.update_check)) },
                text = { Text(stringResource(R.string.update_needs_permission)) },
                confirmButton = {
                    TextButton(onClick = onOpenInstallSettings) {
                        Text(stringResource(R.string.update_open_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
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

/**
 * The system screen where "Always-on VPN" and "Block connections without VPN"
 * live.
 *
 * ACTION_VPN_SETTINGS is API 24+ and present on AOSP and vivo. The fallbacks
 * are for a ROM that hides it: a tap that opens the top-level settings is
 * better than a crash, and the string above tells the reader what to look for.
 */
private fun openSystemVpnSettings(context: Context) {
    val actions = listOf(
        Settings.ACTION_VPN_SETTINGS,
        Settings.ACTION_WIRELESS_SETTINGS,
        Settings.ACTION_SETTINGS,
    )
    for (action in actions) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // Try the next one.
        }
    }
}
