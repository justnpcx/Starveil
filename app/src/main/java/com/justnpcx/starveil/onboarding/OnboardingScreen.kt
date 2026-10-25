package com.justnpcx.starveil.onboarding

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.justnpcx.starveil.core.ui.components.StarveilTextField
import com.justnpcx.starveil.core.ui.components.FieldSurface
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.kernel.vpn.starveilVpnConsent
import com.justnpcx.starveil.core.subscription.DocumentFormat
import com.justnpcx.starveil.core.subscription.ImportFailure
import com.justnpcx.starveil.core.subscription.SubscriptionFailure
import com.justnpcx.starveil.core.ui.components.StarveilButton
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * The first-run flow, three steps, skippable at every one.
 *
 * It sits outside the navigation graph: it is not a destination a user can
 * return to, and it should not be possible to press Back into it. The shell
 * shows it instead of the tabs until the flag is written, then never again.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSubscription by remember { mutableStateOf(false) }
    var subscriptionDraft by remember { mutableStateOf("") }
    var pendingConnect by remember { mutableStateOf(false) }
    var consentDenied by remember { mutableStateOf(false) }

    // The same consent port the dashboard uses. Without this the kernel starts
    // and fails with "VPN consent not granted" — the one path into the tunnel
    // that forgot to ask.
    val vpnConsent = remember(context) { starveilVpnConsent(context) }
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val granted = result.resultCode == Activity.RESULT_OK
        if (granted && pendingConnect) viewModel.connectBest()
        consentDenied = !granted
        pendingConnect = false
    }
    val requestConnect: () -> Unit = {
        consentDenied = false
        val intent = vpnConsent.consentIntent()
        if (intent == null) {
            viewModel.connectBest() // consent already held
        } else {
            pendingConnect = true
            consentLauncher.launch(intent)
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::import)
    }

    // Read once, on entry: the clipboard is a snapshot, and re-reading it on
    // every recomposition would offer a stale offer after the user moved on.
    LaunchedEffect(Unit) {
        viewModel.detectClipboard(clipboardText(context))
    }

    // No background of its own: the ambient gradient painted behind the whole
    // app shows through here too. Onboarding used to fill the page with a flat
    // `bg`, which made the first screen the one place in the app with no depth.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(StarveilSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepDots(step = state.step)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::finish) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }

        when (state.step) {
            WELCOME -> WelcomeStep(onNext = viewModel::next)

            IMPORT -> ImportStep(
                state = state,
                onImport = viewModel::import,
                onImportClipboard = {
                    val text = clipboardText(context)
                    if (text != null) viewModel.import(text)
                },
                onScan = {
                    scanLauncher.launch(
                        ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt(context.getString(R.string.onboarding_scan_prompt))
                            setBeepEnabled(false)
                        },
                    )
                },
                onAddSubscription = { showSubscription = true },
            )

            else -> ConnectStep(
                state = state,
                consentDenied = consentDenied,
                onConnect = requestConnect,
                onFinish = viewModel::finish,
                onBack = viewModel::back,
            )
        }
    }

    if (showSubscription) {
        AlertDialog(
            onDismissRequest = { showSubscription = false },
            title = { Text(stringResource(R.string.onboarding_add_subscription)) },
            text = {
                StarveilTextField(
                    value = subscriptionDraft,
                    onValueChange = { subscriptionDraft = it },
                    label = stringResource(R.string.onboarding_subscription_hint),
                    surface = FieldSurface.Dialog,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSubscription = false
                        viewModel.import(subscriptionDraft)
                        subscriptionDraft = ""
                    },
                ) {
                    Text(stringResource(R.string.onboarding_import_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubscription = false }) {
                    Text(stringResource(R.string.onboarding_cancel))
                }
            },
        )
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.xs)) {
        repeat(3) { index ->
            val active = index <= step
            Box(
                modifier = Modifier
                    .size(width = if (index == step) 24.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            StarveilColors.accent
                        } else {
                            StarveilColors.surfaceGlass
                        },
                    ),
            )
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The same ring language as the dashboard, at rest: a thin accent line,
        // and nothing inside it. The page shows through the hole — a translucent
        // disc in the middle read as a smudge once there was a gradient behind
        // it, which is the cost of having depth.
        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(CircleShape)
                .border(width = 1.5.dp, color = StarveilColors.accent, shape = CircleShape),
        )
        Spacer(modifier = Modifier.height(StarveilSpacing.xl))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = StarveilType.headline,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(StarveilSpacing.sm))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = StarveilType.label,
            color = StarveilColors.ink2,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(StarveilSpacing.xl))
        StarveilButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_start))
        }
    }
}

@Composable
private fun ImportStep(
    state: OnboardingUiState,
    onImport: (String) -> Unit,
    onImportClipboard: () -> Unit,
    onScan: () -> Unit,
    onAddSubscription: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        Spacer(modifier = Modifier.height(StarveilSpacing.xl))
        Text(
            text = stringResource(R.string.onboarding_import_title),
            style = StarveilType.headline,
        )
        Text(
            text = stringResource(R.string.onboarding_import_body),
            style = StarveilType.label,
            color = StarveilColors.ink2,
        )

        // The clipboard offer is the Shadowrocket-grade shortcut: if the link
        // is already on the clipboard, the work is done before the screen
        // paints. It is a highlighted row, not a fourth button, so the three
        // ways in stay three.
        val detected = state.detected
        if (detected != null) {
            Spacer(modifier = Modifier.height(StarveilSpacing.sm))
            BigAction(
                title = stringResource(R.string.onboarding_detected),
                subtitle = detected,
                onClick = { onImport(detected) },
            )
        }

        Spacer(modifier = Modifier.height(StarveilSpacing.md))
        BigAction(
            title = stringResource(R.string.onboarding_import_clipboard),
            subtitle = stringResource(R.string.onboarding_import_clipboard_desc),
            // Re-read, not the snapshot: the button is an explicit "use the
            // clipboard", and what the user just copied may be newer.
            onClick = onImportClipboard,
        )
        BigAction(
            title = stringResource(R.string.onboarding_scan),
            subtitle = stringResource(R.string.onboarding_scan_desc),
            onClick = onScan,
        )
        BigAction(
            title = stringResource(R.string.onboarding_add_subscription),
            subtitle = stringResource(R.string.onboarding_add_subscription_desc),
            onClick = onAddSubscription,
        )

        if (state.importing) {
            Spacer(modifier = Modifier.height(StarveilSpacing.md))
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        when (val result = state.importResult) {
            is ImportResult.Imported -> Text(
                text = if (result.dropped > 0) {
                    stringResource(R.string.onboarding_imported_skipped, result.nodes, result.dropped)
                } else {
                    stringResource(R.string.onboarding_imported, result.nodes)
                },
                style = StarveilType.label,
                color = StarveilColors.accentText,
            )

            is ImportResult.Failed -> Text(
                text = stringResource(R.string.onboarding_import_failed, result.failure.text()),
                style = StarveilType.label,
                color = StarveilColors.errText,
            )

            null -> Unit
        }
    }
}

@Composable
private fun ConnectStep(
    state: OnboardingUiState,
    consentDenied: Boolean,
    onConnect: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        Spacer(modifier = Modifier.height(StarveilSpacing.xl))
        Text(
            text = stringResource(R.string.onboarding_connect_title),
            style = StarveilType.headline,
        )
        Text(
            text = stringResource(R.string.onboarding_connect_body),
            style = StarveilType.label,
            color = StarveilColors.ink2,
        )

        Spacer(modifier = Modifier.height(StarveilSpacing.lg))

        if (consentDenied) {
            Text(
                text = stringResource(R.string.onboarding_consent_denied),
                style = StarveilType.label,
                color = StarveilColors.errText,
            )
        }

        when (val result = state.connectResult) {
            is ConnectResult.Connected -> {
                Text(
                    text = result.name?.let {
                        stringResource(R.string.onboarding_connected, it)
                    } ?: stringResource(R.string.onboarding_connected_unknown),
                    style = StarveilType.headline,
                    color = StarveilColors.accentText,
                )
                Spacer(modifier = Modifier.height(StarveilSpacing.md))
                StarveilButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_done))
                }
            }

            ConnectResult.NoNodes -> {
                Text(
                    text = stringResource(R.string.onboarding_no_nodes),
                    style = StarveilType.label,
                    color = StarveilColors.errText,
                )
                StarveilButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_back))
                }
            }

            is ConnectResult.Failed -> {
                Text(
                    text = result.reason?.let {
                        stringResource(R.string.onboarding_connect_failed, it)
                    } ?: stringResource(R.string.onboarding_connect_failed_unknown),
                    style = StarveilType.label,
                    color = StarveilColors.errText,
                )
                StarveilButton(
                    onClick = onConnect,
                    enabled = !state.connecting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_retry))
                }
                TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }

            null -> {
                StarveilButton(
                    onClick = onConnect,
                    enabled = !state.connecting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.onboarding_connect_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun BigAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    StarveilButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = StarveilSpacing.sm),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(text = title, style = StarveilType.headline)
            Text(
                text = subtitle,
                style = StarveilType.label,
                maxLines = 1,
                // A glass button's subtitle is secondary ink, not the
                // page's own colour: there is no filled plate to sit on.
                color = StarveilColors.ink2,
            )
        }
    }
}

/** The same mapping the node list uses, with this flow's own copy. */
@Composable
private fun ImportFailure.text(): String = when (this) {
    ImportFailure.NothingToImport -> stringResource(R.string.onboarding_failure_nothing)
    ImportFailure.LinkUnparseable -> stringResource(R.string.onboarding_failure_link)
    is ImportFailure.Subscription -> reason.text()
}

@Composable
private fun SubscriptionFailure.text(): String = when (this) {
    SubscriptionFailure.Network -> stringResource(R.string.onboarding_failure_network)
    is SubscriptionFailure.Http -> stringResource(R.string.onboarding_failure_http, code)
    is SubscriptionFailure.MalformedDocument -> {
        val at = line
        when {
            format == DocumentFormat.YAML && at != null ->
                stringResource(R.string.onboarding_failure_yaml_line, at)
            format == DocumentFormat.JSON && at != null ->
                stringResource(R.string.onboarding_failure_json_line, at)
            else -> stringResource(R.string.onboarding_failure_document)
        }
    }
    SubscriptionFailure.NoUsableNodes -> stringResource(R.string.onboarding_failure_no_nodes)
    SubscriptionFailure.SaveFailed -> stringResource(R.string.onboarding_failure_save)
}

private fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

private const val WELCOME = 0
private const val IMPORT = 1
