package com.justnpcx.starveil.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
    NavHost(
        navController = navController,
        startDestination = Destination.DASHBOARD.route,
        modifier = modifier,
    ) {
        composable(Destination.DASHBOARD.route) { DashboardScreen() }
        composable(Destination.PROFILES.route) { ProfilesScreen() }
        composable(Destination.RULES.route) { RulesScreen() }
        composable(Destination.LOGS.route) { LogsScreen() }
        composable(Destination.SETTINGS.route) { SettingsScreen() }
    }
}
