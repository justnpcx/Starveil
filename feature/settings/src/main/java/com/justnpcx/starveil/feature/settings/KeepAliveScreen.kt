package com.justnpcx.starveil.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * The three switches a Chinese ROM needs before it will let a VPN live.
 *
 * Not a wizard and not a checklist with ticks: each row opens the page where
 * the switch actually is, because the app cannot flip any of them itself. The
 * battery row is the only one whose state can be read, so it is the only one
 * that shows a verdict; the other two say what they do and get out of the way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepAliveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var exempt by remember {
        mutableStateOf(KeepAliveTargets.isExemptFromBatteryOptimizations(context))
    }
    val hasVendorPage = remember { KeepAliveTargets.hasVendorAutostartPage(context) }

    // The switch lives in Settings; coming back is when the verdict changed.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exempt = KeepAliveTargets.isExemptFromBatteryOptimizations(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.keepalive_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.keepalive_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(StarveilSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(StarveilSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.keepalive_intro),
                style = StarveilType.label,
                color = StarveilColors.ink2,
            )

            KeepAliveRow(
                title = stringResource(R.string.keepalive_battery_title),
                body = stringResource(R.string.keepalive_battery_body),
                status = stringResource(
                    if (exempt) {
                        R.string.keepalive_battery_on
                    } else {
                        R.string.keepalive_battery_off
                    },
                ),
                statusIsWarning = !exempt,
                action = stringResource(R.string.keepalive_action_open),
                onAction = {
                    KeepAliveTargets.launch(context, KeepAliveTargets.batteryOptimizationIntent(context))
                },
            )

            KeepAliveRow(
                title = stringResource(R.string.keepalive_autostart_title),
                body = stringResource(
                    if (hasVendorPage) {
                        R.string.keepalive_autostart_body
                    } else {
                        // No vendor page on this ROM: say where the button goes
                        // instead of pretending a page exists.
                        R.string.keepalive_autostart_fallback
                    },
                ),
                status = null,
                statusIsWarning = false,
                action = stringResource(R.string.keepalive_action_open),
                onAction = {
                    KeepAliveTargets.launch(context, KeepAliveTargets.autostartIntent(context))
                },
            )

            KeepAliveRow(
                title = stringResource(R.string.keepalive_details_title),
                body = stringResource(R.string.keepalive_details_body),
                status = null,
                statusIsWarning = false,
                action = stringResource(R.string.keepalive_action_open),
                onAction = {
                    KeepAliveTargets.launch(context, KeepAliveTargets.appDetailsIntent(context))
                },
            )
        }
    }
}

@Composable
private fun KeepAliveRow(
    title: String,
    body: String,
    status: String?,
    statusIsWarning: Boolean,
    action: String,
    onAction: () -> Unit,
) {
    StarveilCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(StarveilSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = StarveilType.headline,
                    modifier = Modifier.weight(1f),
                )
                if (status != null) {
                    Text(
                        text = status,
                        style = StarveilType.label,
                        color = if (statusIsWarning) {
                            StarveilColors.err
                        } else {
                            StarveilColors.accentA
                        },
                    )
                }
            }
            Text(
                text = body,
                style = StarveilType.label,
                color = StarveilColors.ink2,
                modifier = Modifier.padding(top = StarveilSpacing.xs),
            )
            TextButton(onClick = onAction, modifier = Modifier.padding(top = StarveilSpacing.xs)) {
                Text(action)
            }
        }
    }
}
