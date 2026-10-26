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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
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
import com.justnpcx.starveil.core.ui.components.StarveilTextField
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.LeakCheckRecord
import com.justnpcx.starveil.core.model.LocalApi
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.justnpcx.starveil.core.data.update.UpdateFailure
import com.justnpcx.starveil.core.data.update.UpdateInstaller
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.components.StarveilDivider
import com.justnpcx.starveil.core.ui.components.StarveilGroupHeader
import com.justnpcx.starveil.core.ui.components.StarveilPageHeader
import com.justnpcx.starveil.core.ui.components.StarveilSwitch
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

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
            // self-check and the version sit below the fold with no way to
            // reach them.
            .verticalScroll(rememberScrollState()),
    ) {
        StarveilPageHeader(title = stringResource(R.string.settings_title))

        // ---- Connection ----------------------------------------------------
        StarveilGroupHeader(stringResource(R.string.settings_group_connection))
        SettingsCard {
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
            // rather than duplicating the switch is deliberate — only the
            // system can turn its own lock on, and pretending otherwise would
            // be a lie.
            StarveilDivider(startInset = StarveilSpacing.lg)
            // The link sits on its own line: beside the paragraph it stole the
            // width the sentence needed and broke it in the middle.
            Column(
                modifier = Modifier.padding(
                    start = StarveilSpacing.lg,
                    end = StarveilSpacing.lg,
                    top = StarveilSpacing.md,
                    bottom = StarveilSpacing.sm,
                ),
            ) {
                Text(
                    text = stringResource(R.string.kill_switch_system_desc),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                )
                Spacer(modifier = Modifier.height(StarveilSpacing.xs))
                LinkText(
                    text = stringResource(R.string.kill_switch_system_link),
                    onClick = { openSystemVpnSettings(context) },
                )
            }
            StarveilDivider(startInset = StarveilSpacing.lg)
            SwitchRow(
                title = stringResource(R.string.auto_reconnect_title),
                description = stringResource(R.string.auto_reconnect_desc),
                checked = state.autoReconnect,
                onCheckedChange = viewModel::setAutoReconnect,
            )
            StarveilDivider(startInset = StarveilSpacing.lg)
            SwitchRow(
                title = stringResource(R.string.auto_switch_title),
                description = stringResource(R.string.auto_switch_desc),
                checked = state.autoSwitchOnDrop,
                onCheckedChange = viewModel::setAutoSwitchOnDrop,
            )
        }

        // ---- Routing and background ----------------------------------------
        // One group, because the two entries answer one question: what happens
        // to traffic this app is not sending through the tunnel, and what keeps
        // the tunnel alive. Two groups of one row each was two captions and a
        // screenful of air.
        StarveilGroupHeader(stringResource(R.string.settings_group_routing))
        SettingsCard {
            EntryRow(
                title = stringResource(R.string.per_app_entry_title),
                description = if (state.perAppDirectCount == 0) {
                    stringResource(R.string.per_app_entry_desc)
                } else {
                    stringResource(R.string.per_app_entry_count, state.perAppDirectCount)
                },
                onClick = onOpenPerApp,
            )
            StarveilDivider(startInset = StarveilSpacing.lg)
            EntryRow(
                title = stringResource(R.string.keepalive_entry_title),
                description = stringResource(R.string.keepalive_entry_desc),
                onClick = onOpenKeepAlive,
            )
        }

        // ---- Data, security and diagnostics --------------------------------
        // Everything that answers "what is going on": the backup file, the leak
        // self-check, the DNS numbers and the local API. The new diagnostics
        // from this task book belong here too, so they do not start a fifth
        // group of their own.
        StarveilGroupHeader(stringResource(R.string.settings_group_diagnostics))
        SettingsCard {
            Row(
                modifier = Modifier.padding(
                    start = StarveilSpacing.lg,
                    end = StarveilSpacing.lg,
                    top = StarveilSpacing.md,
                    bottom = StarveilSpacing.xs,
                ),
            ) {
                Text(
                    text = stringResource(R.string.backup_explainer),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                )
            }
            Row(
                modifier = Modifier.padding(
                    start = StarveilSpacing.lg,
                    end = StarveilSpacing.lg,
                    top = StarveilSpacing.xs,
                    bottom = StarveilSpacing.md,
                ),
                horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.md),
            ) {
                LinkText(
                    text = stringResource(R.string.backup_export),
                    onClick = { askingForExport = true },
                )
                LinkText(
                    text = stringResource(R.string.backup_restore),
                    onClick = { openBackup.launch(arrayOf("application/octet-stream", "*/*")) },
                )
            }
            StarveilDivider(startInset = StarveilSpacing.lg)
            Row(
                modifier = Modifier.padding(
                    start = StarveilSpacing.lg,
                    end = StarveilSpacing.lg,
                    top = StarveilSpacing.md,
                    bottom = StarveilSpacing.xs,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = leakCheckSummary(state.lastLeakCheck),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(StarveilSpacing.md))
                LinkText(
                    text = stringResource(R.string.leak_check_run),
                    onClick = onRunLeakCheck,
                )
            }
            Spacer(modifier = Modifier.height(StarveilSpacing.sm))
            StarveilDivider(startInset = StarveilSpacing.lg)
            // The ring only changes while something resolves, so the row reads
            // it on a slow tick rather than making every lookup flow into the
            // UI: a settings screen has no business observing the DNS path.
            LaunchedEffect(Unit) {
                while (true) {
                    viewModel.refreshDnsStats()
                    delay(DNS_STATS_REFRESH_MS)
                }
            }
            SwitchRow(
                title = stringResource(R.string.settings_dns_metrics_title),
                description = stringResource(R.string.settings_dns_metrics_desc),
                checked = state.dnsMetricsEnabled,
                onCheckedChange = viewModel::setDnsMetricsEnabled,
            )
            if (state.dnsMetricsEnabled) {
                StarveilDivider(startInset = StarveilSpacing.lg)
                Column(
                    modifier = Modifier.padding(
                        start = StarveilSpacing.lg,
                        end = StarveilSpacing.lg,
                        top = StarveilSpacing.md,
                        bottom = StarveilSpacing.sm,
                    ),
                ) {
                    Text(
                        text = if (state.dnsStats.samples == 0) {
                            stringResource(R.string.settings_dns_metrics_empty)
                        } else {
                            stringResource(
                                R.string.settings_dns_metrics_stats,
                                state.dnsStats.samples,
                                state.dnsStats.p50Ms,
                                state.dnsStats.p95Ms,
                                state.dnsStats.fallbackPct,
                                state.dnsStats.cacheHitPct,
                            )
                        },
                        style = StarveilType.log,
                        color = StarveilColors.ink2,
                    )
                    Spacer(modifier = Modifier.height(StarveilSpacing.xs))
                    LinkText(
                        text = stringResource(R.string.settings_dns_metrics_clear),
                        enabled = state.dnsStats.samples > 0,
                        onClick = viewModel::clearDnsStats,
                    )
                }
            }
            StarveilDivider(startInset = StarveilSpacing.lg)
            SwitchRow(
                title = stringResource(R.string.settings_local_api_title),
                description = stringResource(R.string.settings_local_api_desc, LocalApi.address),
                checked = state.localApiEnabled,
                onCheckedChange = viewModel::setLocalApiEnabled,
            )
            if (state.localApiEnabled) {
                StarveilDivider(startInset = StarveilSpacing.lg)
                Column(
                    modifier = Modifier.padding(
                        start = StarveilSpacing.lg,
                        end = StarveilSpacing.lg,
                        top = StarveilSpacing.md,
                        bottom = StarveilSpacing.sm,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.settings_local_api_address, LocalApi.address),
                        style = StarveilType.log,
                        color = StarveilColors.ink2,
                    )
                    Spacer(modifier = Modifier.height(StarveilSpacing.xs))
                    Text(
                        text = state.apiToken,
                        style = StarveilType.log,
                        color = StarveilColors.ink2,
                    )
                }
                Row(
                    modifier = Modifier.padding(
                        start = StarveilSpacing.lg,
                        end = StarveilSpacing.lg,
                        bottom = StarveilSpacing.md,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.lg),
                ) {
                    LinkText(
                        text = stringResource(R.string.settings_local_api_copy),
                        enabled = state.apiToken.isNotEmpty(),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Starveil local API token", state.apiToken),
                            )
                        },
                    )
                    LinkText(
                        text = stringResource(R.string.settings_local_api_reset),
                        enabled = state.apiToken.isNotEmpty(),
                        onClick = { confirmingTokenReset = true },
                    )
                }
            }
        }

        // ---- About ----------------------------------------------------------
        StarveilGroupHeader(stringResource(R.string.settings_group_about))
        SettingsCard {
            ValueRow(
                title = stringResource(R.string.settings_version_title),
                value = appVersion.orEmpty(),
            )
            StarveilDivider(startInset = StarveilSpacing.lg)
            ValueRow(
                title = stringResource(R.string.settings_kernel_title),
                // The core names itself as \`1.14.3-<commit>\`; the commit is a
                // build detail, and this row is a version.
                value = state.kernelVersion.takeIf { it.isNotBlank() }
                    ?.substringBefore('-')
                    ?: stringResource(R.string.settings_kernel_unknown),
            )
            StarveilDivider(startInset = StarveilSpacing.lg)
            Row(
                modifier = Modifier.padding(
                    start = StarveilSpacing.lg,
                    end = StarveilSpacing.lg,
                    top = StarveilSpacing.md,
                    bottom = StarveilSpacing.md,
                ),
                horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.lg),
            ) {
                LinkText(
                    text = stringResource(R.string.update_check),
                    enabled = updateState !is UpdateUiState.Checking &&
                        updateState !is UpdateUiState.Downloading,
                    onClick = { viewModel.checkForUpdates(appVersion.orEmpty()) },
                )
                LinkText(
                    text = stringResource(R.string.settings_privacy),
                    onClick = onOpenPrivacy,
                )
            }
            // The repository, not a marketing page: the changelog is the same
            // file the release notes are cut from, and the issue tracker is
            // where a diagnostic bundle is meant to end up. All three leave for
            // the system browser — an in-app WebView would need its own
            // permission surface and would be the one place in this app that
            // loads remote content.
            StarveilDivider(startInset = StarveilSpacing.lg)
            Column(
                modifier = Modifier.padding(
                    start = StarveilSpacing.lg,
                    end = StarveilSpacing.lg,
                    top = StarveilSpacing.md,
                    bottom = StarveilSpacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
            ) {
                LinkText(
                    text = stringResource(R.string.settings_github_repo),
                    onClick = { openUrl(context, PROJECT_REPO) },
                )
                LinkText(
                    text = stringResource(R.string.settings_github_changelog),
                    onClick = { openUrl(context, PROJECT_CHANGELOG) },
                )
                LinkText(
                    text = stringResource(R.string.settings_github_issues),
                    onClick = { openUrl(context, PROJECT_ISSUES) },
                )
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
 * The failure, in the user's language.
 *
 * The data layer returns a closed set of reasons precisely so this can be a
 * `when` the compiler checks: a new reason without a sentence here is a build
 * error, not a dialog showing an internal string to someone who cannot act on
 * it. The sentence follows "Could not check for updates:" — a fragment, no
 * capital, no full stop.
 */
@Composable
private fun UpdateFailure.describe(): String = when (this) {
    UpdateFailure.NoRelease -> stringResource(R.string.update_error_no_release)
    UpdateFailure.RateLimited -> stringResource(R.string.update_error_rate_limited)
    is UpdateFailure.Http -> stringResource(R.string.update_error_http, code)
    UpdateFailure.Network -> stringResource(R.string.update_error_network)
    UpdateFailure.Unreadable -> stringResource(R.string.update_error_unreadable)
    is UpdateFailure.NoApkForDevice -> {
        // Local, because a public property from another module does not smart
        // cast.
        val deviceAbi = abi
        if (deviceAbi == null) {
            stringResource(R.string.update_error_no_apk)
        } else {
            stringResource(R.string.update_error_no_apk_for, deviceAbi)
        }
    }
    UpdateFailure.NoDownloadUrl -> stringResource(R.string.update_error_no_url)
    UpdateFailure.SizeMismatch -> stringResource(R.string.update_error_size)
    UpdateFailure.ChecksumMismatch -> stringResource(R.string.update_error_checksum)
    UpdateFailure.SignatureMismatch -> stringResource(R.string.update_error_signature)
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
            text = { Text(stringResource(R.string.update_failed, state.reason.describe())) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.per_app_close)) }
            },
        )

        is UpdateUiState.DownloadFailed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_check)) },
            text = { Text(stringResource(R.string.update_download_failed, state.reason.describe())) },
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
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                )
                StarveilTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = stringResource(R.string.backup_passphrase),
                                        visualTransformation = PasswordVisualTransformation(),
                    supportingText = stringResource(R.string.backup_passphrase_min, MIN_PASSPHRASE),
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

/** How often the DNS row re-reads its ring while the screen is open. */
private const val DNS_STATS_REFRESH_MS = 2_000L

private fun defaultBackupFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "starveil-$stamp.svb"
}

/** A glass card with the page's own side margin. */
@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    StarveilCard(
        modifier = Modifier.padding(horizontal = StarveilSpacing.pageMargin),
        content = content,
    )
}

/**
 * A text action: accent, on the baseline of the thing it acts on.
 */
@Composable
private fun LinkText(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(StarveilSpacing.xs))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = StarveilType.label,
            color = if (enabled) StarveilColors.accent else StarveilColors.ink3,
        )
    }
}

/** A row that opens something, with a chevron on the right. */
@Composable
private fun EntryRow(
    title: String,
    description: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                start = StarveilSpacing.lg,
                end = StarveilSpacing.lg,
                top = StarveilSpacing.md,
                bottom = StarveilSpacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = StarveilType.headline, color = StarveilColors.ink)
            if (description != null) {
                Text(text = description, style = StarveilType.label, color = StarveilColors.ink2)
            }
        }
        Icon(
            imageVector = StarveilIcons.Chevron,
            contentDescription = null,
            tint = StarveilColors.ink3,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A read-only value: a version, a build, a count. */
@Composable
private fun ValueRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = StarveilSpacing.lg,
                end = StarveilSpacing.lg,
                top = StarveilSpacing.md,
                bottom = StarveilSpacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = StarveilType.headline,
            color = StarveilColors.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = StarveilType.log,
            color = StarveilColors.ink2,
        )
    }
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
            .padding(
                start = StarveilSpacing.lg,
                end = StarveilSpacing.lg,
                top = StarveilSpacing.md,
                bottom = StarveilSpacing.md,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        // The mark is an accent ring with an accent dot, or an empty ring: a Material
        // radio button would bring its own tint and its own size.
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(
                        1.5.dp,
                        if (selected) StarveilColors.accent else StarveilColors.ink3,
                        CircleShape,
                    ),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(StarveilColors.accent),
                )
            }
        }
        Spacer(modifier = Modifier.width(StarveilSpacing.md))
        Column {
            Text(text = label, style = StarveilType.headline, color = StarveilColors.ink)
            if (description != null) {
                Text(
                    text = description,
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
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
            .padding(
                start = StarveilSpacing.lg,
                end = StarveilSpacing.md,
                top = StarveilSpacing.md,
                bottom = StarveilSpacing.md,
            ),
        // The switch sits on the title's line, not halfway down the description.
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = StarveilType.headline, color = StarveilColors.ink)
            Text(
                text = description,
                style = StarveilType.label,
                color = StarveilColors.ink2,
            )
        }
        Spacer(modifier = Modifier.width(StarveilSpacing.md))
        // The whole row is the target, so the switch itself is not one: it
        // would toggle twice.
        StarveilSwitch(checked = checked, onCheckedChange = null)
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
/**
 * Where the project lives. Not secrets and not configuration — the same three
 * addresses anyone reading the About card could type in.
 */
private const val PROJECT_REPO = "https://github.com/justnpcx/Starveil"
private const val PROJECT_CHANGELOG = "https://github.com/justnpcx/Starveil/blob/main/CHANGELOG.md"
private const val PROJECT_ISSUES = "https://github.com/justnpcx/Starveil/issues/new"

/**
 * Hands a URL to whatever the system uses for links.
 *
 * A device with no browser at all is the one case that can fail, and it fails
 * silently unless it is caught — a link that does nothing is worse than a link
 * that says why.
 */
private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.settings_link_failed, Toast.LENGTH_SHORT).show()
    }
}

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
