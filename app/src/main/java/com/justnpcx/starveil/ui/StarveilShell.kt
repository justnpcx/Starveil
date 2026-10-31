package com.justnpcx.starveil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.ui.components.LocalTopBarActions
import com.justnpcx.starveil.core.ui.components.StarveilBackground
import com.justnpcx.starveil.core.ui.components.TopBarActionsState
import com.justnpcx.starveil.core.ui.components.StarveilIconAction
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/** The four destinations the bar lists. Everything else is a page below one. */
private val TOP_LEVEL_ROUTES = setOf("dashboard", "profiles", "rules", "hub")

/**
 * Routes that own the whole window.
 *
 * The scanner and the node editor are full-screen on purpose — the camera
 * preview has no room for a bar, and the editor is a form with its own footer —
 * so the shell draws nothing over them, not even the navigation bar.
 */
private fun ownsWindow(route: String?): Boolean =
    route == "scan" || route?.startsWith("editor/") == true

/**
 * The app's frame: a top bar, the page, and the navigation bar.
 *
 * One navigation system. The floating capsule and the drawer are gone; the bar
 * is the platform's, full width, with the four destinations that carry the
 * app's own pages, and a hub for everything else.
 */
@Composable
fun StarveilShell(
    navController: NavHostController,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    snackbarHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val fullWindow = ownsWindow(route)
    val secondary = route != null && !fullWindow && route !in TOP_LEVEL_ROUTES
    val title = titleFor(route)
    val actions = remember { TopBarActionsState() }

    fun go(section: NavSection) {
        navController.navigate(routeFor(section)) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            // The hub is a page rather than a tab with its own history: its
            // item always lands on the list, even from a page opened out of
            // it. Restoring the saved stack here would put the user back on
            // the scene they had open and leave no way to reach the hub at
            // all — which is exactly what it did.
            restoreState = section != NavSection.MORE
        }
    }

    // The page's own background lives here, not in MainActivity: the wash is
    // allowed on the home page only, and this is the one place that knows which
    // page is showing.
    StarveilBackground(showWash = route == "dashboard") {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            // Zero: the top bar handles the status bar for every page it
            // frames, and the navigation bar handles the gesture inset for
            // itself. Applying them here as well would double both.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (!fullWindow) {
                    StarveilTopBar(
                        title = title,
                        darkTheme = darkTheme,
                        showBack = secondary,
                        onBack = { navController.popBackStack() },
                        showThemeToggle = !secondary,
                        onToggleTheme = onToggleTheme,
                        actions = actions.content,
                    )
                }
            },
            bottomBar = {
                if (!fullWindow) {
                    StarveilNavBar(
                        currentRoute = route,
                        onSelect = ::go,
                    )
                }
            },
            snackbarHost = snackbarHost,
            content = { padding ->
                CompositionLocalProvider(LocalTopBarActions provides actions) {
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        content(PaddingValues(0.dp))
                    }
                }
            },
        )
    }
}

/**
 * The page's name, and the one global action.
 *
 * 56dp, and no menu glyph: the navigation bar is the navigation. A secondary
 * page gets the arrow back to the page it came from — the hub, or the settings
 * page for the pages that hang off it — and keeps its own title.
 */
@Composable
private fun StarveilTopBar(
    title: String,
    darkTheme: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    showThemeToggle: Boolean,
    onToggleTheme: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StarveilColors.bg)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = StarveilSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            StarveilIconAction(
                icon = StarveilIcons.Back,
                contentDescription = stringResource(R.string.nav_back),
                onClick = onBack,
                tint = StarveilColors.ink,
            )
        }
        Text(
            text = title,
            style = StarveilType.pageTitle,
            color = StarveilColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (showBack) StarveilSpacing.xs else StarveilSpacing.md),
        )
        actions?.invoke(this)
        if (showThemeToggle) {
            StarveilIconAction(
                icon = if (darkTheme) StarveilIcons.Sun else StarveilIcons.Moon,
                contentDescription = stringResource(
                    if (darkTheme) R.string.theme_to_light else R.string.theme_to_dark,
                ),
                onClick = onToggleTheme,
                tint = StarveilColors.ink,
            )
        }
    }
}

/** The bar's destination for a section. */
private fun routeFor(section: NavSection): String = when (section) {
    NavSection.HOME -> "home"
    NavSection.NODES -> "nodes"
    NavSection.RULES -> "rules_tab"
    NavSection.MORE -> "hub"
}

/** Every route the shell can frame, and the word it wears. */
@Composable
private fun titleFor(route: String?): String = stringResource(
    when (route) {
        "profiles" -> R.string.nav_nodes
        "rules" -> R.string.nav_rules
        "hub" -> R.string.nav_more
        "scenes", "scenes/new?name={name}" -> R.string.nav_scenes
        "logs" -> R.string.nav_logs
        "settings" -> R.string.nav_settings
        "ruletest" -> R.string.nav_diagnostics
        "about" -> R.string.nav_about
        "perapp" -> R.string.nav_per_app
        "keepalive" -> R.string.nav_keep_alive
        "connections" -> R.string.nav_connections
        "dnslog" -> R.string.nav_dns_log
        "privacy" -> R.string.nav_privacy
        else -> R.string.nav_home
    },
)
