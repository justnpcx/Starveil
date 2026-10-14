package com.justnpcx.starveil.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.justnpcx.starveil.core.kernel.vpn.starveilVpnConsent
import com.justnpcx.starveil.latency.NodeLatencyTesterEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import com.justnpcx.starveil.feature.dashboard.DashboardScreen
import com.justnpcx.starveil.feature.logs.LogsScreen
import com.justnpcx.starveil.feature.profiles.ProfilesScreen
import com.justnpcx.starveil.feature.rules.RulesScreen
import com.justnpcx.starveil.feature.settings.SettingsScreen

/** Destinations. Adding a screen = adding one entry here + one composable below. */
enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    DASHBOARD("dashboard", "Dashboard", StarveilIcons.Dashboard),
    PROFILES("profiles", "Nodes", Icons.AutoMirrored.Filled.List),
    RULES("rules", "Rules", StarveilIcons.Route),
    LOGS("logs", "Logs", StarveilIcons.Terminal),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

@Composable
fun StarveilNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The only kernel surface :app touches: the narrow consent port.
    val vpnConsent = remember(context) { starveilVpnConsent(context) }
    var pendingConnect by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Latency testing needs the kernel, so it lives in :app and the Nodes
    // screen only gets a callback.
    val latencyTester = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NodeLatencyTesterEntryPoint::class.java,
        ).nodeLatencyTester()
    }
    val scope = rememberCoroutineScope()
    val onTestLatency: () -> Unit = { scope.launch { latencyTester.testSelectedNode() } }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        // Only continue on an explicit grant; a denial leaves the connect
        // alone and the kernel start will report PermissionDenied.
        if (result.resultCode == Activity.RESULT_OK) pendingConnect?.invoke()
        pendingConnect = null
    }

    val requestVpnConsent: (onGranted: () -> Unit) -> Unit = { onGranted ->
        val intent = vpnConsent.consentIntent()
        if (intent == null) {
            onGranted() // consent already held
        } else {
            pendingConnect = onGranted
            consentLauncher.launch(intent)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Destination.DASHBOARD.route,
        modifier = modifier,
    ) {
        composable(Destination.DASHBOARD.route) {
            DashboardScreen(requestVpnConsent = requestVpnConsent)
        }
        composable(Destination.PROFILES.route) {
            ProfilesScreen(onTestLatency = onTestLatency)
        }
        composable(Destination.RULES.route) { RulesScreen() }
        composable(Destination.LOGS.route) { LogsScreen() }
        composable(Destination.SETTINGS.route) { SettingsScreen() }
    }
}
