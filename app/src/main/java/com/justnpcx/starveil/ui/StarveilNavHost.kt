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
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.justnpcx.starveil.BuildConfig
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.kernel.vpn.starveilVpnConsent
import com.justnpcx.starveil.latency.NodeLatencyTesterEntryPoint
import com.justnpcx.starveil.leak.LeakCheckRunnerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import com.justnpcx.starveil.feature.dashboard.DashboardScreen
import com.justnpcx.starveil.feature.logs.LogsScreen
import com.justnpcx.starveil.feature.profiles.ProfilesScreen
import com.justnpcx.starveil.feature.rules.RulesScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.di.ProtocolRegistryEntryPoint
import com.justnpcx.starveil.feature.editor.ProfileEditorScreen
import com.justnpcx.starveil.feature.settings.PerAppScreen
import com.justnpcx.starveil.feature.settings.PrivacyScreen
import com.justnpcx.starveil.feature.settings.SettingsScreen

/** Destinations. Adding a screen = adding one entry here + one composable below. */
enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    // "Home", not "Dashboard": five tabs leave ~72dp per label, and the longer
    // word wrapped to two lines on device (the trailing "d" dropped onto its
    // own row). The screen itself is still the dashboard.
    DASHBOARD("dashboard", R.string.nav_home, StarveilIcons.Dashboard),
    PROFILES("profiles", R.string.nav_nodes, Icons.AutoMirrored.Filled.List),
    RULES("rules", R.string.nav_rules, StarveilIcons.Route),
    LOGS("logs", R.string.nav_logs, StarveilIcons.Terminal),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}

/** Not a tab: reached from Settings, which is where the exception list lives. */
private const val PER_APP_ROUTE = "perapp"

/** Also not a tab: the policy is read once, from the About section. */
private const val PRIVACY_ROUTE = "privacy"

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
    val onTestLatency: ((String) -> Unit) -> Unit = { onResult ->
        scope.launch { onResult(latencyTester.testSelectedNode()) }
    }

    // The leak self-check watches the VpnService, so it also lives in :app and
    // Settings only gets a callback.
    val leakCheckRunner = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            LeakCheckRunnerEntryPoint::class.java,
        ).leakCheckRunner()
    }
    val onRunLeakCheck: () -> Unit = {
        scope.launch { leakCheckRunner.run() }
    }

    // One editor route per protocol, assembled from the registry: adding a
    // protocol adds its editor without touching this graph. The route strings
    // live on the descriptors (ProtocolDescriptor.editorRoute).
    val descriptors = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ProtocolRegistryEntryPoint::class.java,
        ).protocolRegistry().all()
    }
    val editorRouteFor: (ProfileType) -> String = { type ->
        descriptors.firstOrNull { it.type == type }?.editorRoute ?: Destination.PROFILES.route
    }

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
            ProfilesScreen(
                onTestLatency = onTestLatency,
                onNewNode = { type -> navController.navigate(editorRouteFor(type)) },
                onEditNode = { type, id -> navController.navigate("${editorRouteFor(type)}?id=$id") },
            )
        }
        composable(Destination.RULES.route) { RulesScreen() }
        composable(Destination.LOGS.route) { LogsScreen() }
        composable(Destination.SETTINGS.route) {
            SettingsScreen(
                onRunLeakCheck = onRunLeakCheck,
                onOpenPerApp = { navController.navigate(PER_APP_ROUTE) },
                onOpenPrivacy = { navController.navigate(PRIVACY_ROUTE) },
                // A build with no DSN has no crash reporting, so the switch is
                // hidden rather than shown doing nothing.
                crashReportingAvailable = BuildConfig.SENTRY_DSN.isNotBlank(),
            )
        }
        composable(PER_APP_ROUTE) {
            PerAppScreen(onBack = { navController.popBackStack() })
        }
        composable(PRIVACY_ROUTE) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }

        descriptors.forEach { descriptor ->
            composable(
                route = "${descriptor.editorRoute}?id={id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.LongType
                        // Absent means "create a new node".
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                ProfileEditorScreen(
                    type = descriptor.type,
                    profileId = entry.arguments?.getLong("id") ?: 0L,
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}
