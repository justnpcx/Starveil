package com.justnpcx.starveil.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.justnpcx.starveil.BuildConfig
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.kernel.vpn.starveilVpnConsent
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import com.justnpcx.starveil.feature.dashboard.DashboardScreen
import com.justnpcx.starveil.feature.logs.LogsScreen
import com.justnpcx.starveil.feature.profiles.ProfilesScreen
import com.justnpcx.starveil.feature.rules.RulesScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.probe.LeakCheckRunner
import com.justnpcx.starveil.core.probe.NodeLatencyTester
import com.justnpcx.starveil.deeplink.DeepLinkInbox
import com.justnpcx.starveil.di.ProtocolRegistryEntryPoint
import com.justnpcx.starveil.feature.editor.ProfileEditorScreen
import com.justnpcx.starveil.feature.rules.ScenesScreen
import com.justnpcx.starveil.feature.settings.KeepAliveScreen
import com.justnpcx.starveil.feature.settings.PerAppScreen
import com.justnpcx.starveil.feature.settings.PrivacyScreen
import com.justnpcx.starveil.feature.settings.ConnectionsScreen
import com.justnpcx.starveil.feature.settings.DnsLogScreen
import com.justnpcx.starveil.feature.settings.RuleTestScreen
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
    PROFILES("profiles", R.string.nav_nodes, StarveilIcons.Nodes),
    RULES("rules", R.string.nav_rules, StarveilIcons.Route),
    LOGS("logs", R.string.nav_logs, StarveilIcons.Terminal),
    SETTINGS("settings", R.string.nav_settings, StarveilIcons.Settings),
}

/** Not a tab: reached from Settings, which is where the exception list lives. */
private const val PER_APP_ROUTE = "perapp"
private const val SCENES_ROUTE = "scenes"
private const val SCENES_NEW_ROUTE = "scenes/new?name={name}"
private const val KEEP_ALIVE_ROUTE = "keepalive"
private const val RULE_TEST_ROUTE = "ruletest"
private const val CONNECTIONS_ROUTE = "connections"
private const val DNS_LOG_ROUTE = "dnslog"

/** Also not a tab: the policy is read once, from the About section. */
private const val PRIVACY_ROUTE = "privacy"

@Composable
fun StarveilNavHost(
    navController: NavHostController,
    latencyTester: NodeLatencyTester,
    leakCheckRunner: LeakCheckRunner,
    deepLinks: DeepLinkInbox,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The only kernel surface :app touches: the narrow consent port.
    val vpnConsent = remember(context) { starveilVpnConsent(context) }
    var pendingConnect by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Both come from the Activity: they need the kernel, they live in
    // :core:probe, and the screens below only receive callbacks.
    val scope = rememberCoroutineScope()
    val onTestLatency: ((String) -> Unit) -> Unit = { onResult ->
        scope.launch { onResult(latencyTester.testSelectedNode()) }
    }
    // The same probe for one node: tapping a card's badge should measure that
    // card, not whichever node happens to be selected.
    val onTestNode: (Long, (String) -> Unit) -> Unit = { id, onResult ->
        scope.launch { onResult(latencyTester.testNode(id)) }
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

    // A link that arrived before there was a screen — a cold launch from a
    // share sheet, or onboarding still to get through. It waits in the inbox
    // until this composition exists, then the Nodes tab is where it belongs:
    // that is the screen that can show what the import did.
    val pendingLink by deepLinks.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingLink) {
        if (pendingLink == null) return@LaunchedEffect
        navController.navigate("nodes") {
            // The tab bar's own rule: one entry per tab, state restored. A
            // link tapped while on Settings must not stack a second Nodes.
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier,
    ) {
        navigation(startDestination = Destination.DASHBOARD.route, route = "home") {
        composable(Destination.DASHBOARD.route) {
            DashboardScreen(
                requestVpnConsent = requestVpnConsent,
                onOpenNodes = {
                    navController.navigate("nodes") {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        }

        navigation(startDestination = Destination.PROFILES.route, route = "nodes") {
        composable(Destination.PROFILES.route) {
            ProfilesScreen(
                onTestLatency = onTestLatency,
                onTestNode = onTestNode,
                onNewNode = { type -> navController.navigate(editorRouteFor(type)) },
                onEditNode = { type, id -> navController.navigate("${editorRouteFor(type)}?id=$id") },
                incomingLink = pendingLink,
                onLinkHandled = { deepLinks.consume() },
                onNewScene = { name ->
                    navController.navigate("scenes/new?name=" + android.net.Uri.encode(name))
                },
            )
        }
        }

        navigation(startDestination = Destination.RULES.route, route = "rules_tab") {
        composable(Destination.RULES.route) {
            RulesScreen(
                onOpenPerApp = { navController.navigate(PER_APP_ROUTE) },
                onOpenScenes = { navController.navigate("scenes_tab") },
            )
        }
        }

        navigation(startDestination = Destination.LOGS.route, route = "logs_tab") {
        composable(Destination.LOGS.route) { LogsScreen() }
        }

        navigation(startDestination = Destination.SETTINGS.route, route = "settings_tab") {
        composable(Destination.SETTINGS.route) {
            SettingsScreen(
                onRunLeakCheck = onRunLeakCheck,
                onOpenPerApp = { navController.navigate(PER_APP_ROUTE) },
                onOpenKeepAlive = { navController.navigate(KEEP_ALIVE_ROUTE) },
                onOpenRuleTest = { navController.navigate("diagnostics") },
                onOpenConnections = { navController.navigate(CONNECTIONS_ROUTE) },
                onOpenDnsLog = { navController.navigate(DNS_LOG_ROUTE) },
                onOpenPrivacy = { navController.navigate(PRIVACY_ROUTE) },
                // A build with no DSN has no crash reporting, so the switch is
                // hidden rather than shown doing nothing.
                crashReportingAvailable = BuildConfig.SENTRY_DSN.isNotBlank(),
            )
        }
        composable(PER_APP_ROUTE) {
            PerAppScreen(onBack = { navController.popBackStack() })
        }
        }

        navigation(startDestination = SCENES_ROUTE, route = "scenes_tab") {
        composable(SCENES_ROUTE) {
            ScenesScreen()
        }
        composable(
            route = SCENES_NEW_ROUTE,
            arguments = listOf(
                navArgument("name") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            ScenesScreen(draftName = entry.arguments?.getString("name"))
        }
        }

        navigation(startDestination = RULE_TEST_ROUTE, route = "diagnostics") {
        composable(RULE_TEST_ROUTE) {
            RuleTestScreen()
        }
        }

        composable(CONNECTIONS_ROUTE) {
            ConnectionsScreen(onBack = { navController.popBackStack() })
        }
        composable(DNS_LOG_ROUTE) {
            DnsLogScreen(onBack = { navController.popBackStack() })
        }
        composable(KEEP_ALIVE_ROUTE) {
            KeepAliveScreen(onBack = { navController.popBackStack() })
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
