package com.justnpcx.starveil.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.ui.R as UiR
import com.justnpcx.starveil.core.ui.components.StarveilIconAction
import com.justnpcx.starveil.core.ui.components.PageWash
import com.justnpcx.starveil.core.ui.components.StarveilBackground
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType
import kotlinx.coroutines.launch

/** Routes that belong to the drawer, and therefore carry the shell's top bar. */
private val SHELL_ROUTES = setOf(
    "dashboard", "profiles", "rules", "logs", "settings", "scenes",
    "scenes/new?name={name}", "ruletest",
)

/**
 * The app's frame: a drawer, a top bar, and the page.
 *
 * The bottom bar is gone. A page fills the window; the drawer is the only
 * navigation, opened from the menu glyph or a right swipe from the left edge.
 */
@Composable
fun StarveilShell(
    navController: NavHostController,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    snackbarHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    // The shell owns back: Navigation's own callback would pop the current
    // graph before this one sees the press.
    DisposableEffect(navController) {
        navController.enableOnBackPressed(false)
        onDispose { navController.enableOnBackPressed(true) }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showTopBar = route == null || route in SHELL_ROUTES

    val title = when (route) {
        "profiles" -> stringResource(R.string.nav_nodes)
        "rules" -> stringResource(R.string.nav_rules)
        "logs" -> stringResource(R.string.nav_logs)
        "settings" -> stringResource(R.string.nav_settings)
        "scenes", "scenes/new?name={name}" -> stringResource(R.string.nav_scenes)
        "ruletest" -> stringResource(R.string.nav_diagnostics)
        else -> stringResource(R.string.nav_home)
    }

    // The page's own background lives here, not in MainActivity: the wash is
    // allowed on the home page only, and this is the one place that knows which
    // page is showing.
    StarveilBackground(showWash = route == "dashboard") {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = Color.Black.copy(alpha = if (darkTheme) 0.55f else 0.32f),
        drawerContent = {
            StarveilDrawer(
                currentRoute = route,
                onNavigate = { target ->
                    scope.launch { drawerState.close() }
                    navController.navigate(target) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            // Zero: the shell's top bar handles the status bar for the pages
            // it frames, and a secondary page's own top bar handles it for the
            // pages it does not. Applying the inset here as well would double
            // it on both.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (showTopBar) {
                    StarveilTopBar(
                        title = title,
                        darkTheme = darkTheme,
                        onMenu = { scope.launch { drawerState.open() } },
                        onToggleTheme = onToggleTheme,
                    )
                }
            },
            snackbarHost = snackbarHost,
            content = { padding ->
                content(padding)
                // Registered after the NavHost, so it is the callback the
                // dispatcher consults first. Back closes the drawer; on any
                // page other than the home page it goes home; on the home page
                // it is disabled and the system leaves the app.
                BackHandler(enabled = drawerState.isOpen || route != "dashboard") {
                    if (drawerState.isOpen) {
                        scope.launch { drawerState.close() }
                    } else {
                        navController.navigate("home") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            },
        )
    }
    }
}

/** ☰, the page's name, and the one global action. */
@Composable
private fun StarveilTopBar(
    title: String,
    darkTheme: Boolean,
    onMenu: () -> Unit,
    onToggleTheme: () -> Unit,
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
        StarveilIconAction(
            icon = StarveilIcons.Menu,
            contentDescription = stringResource(R.string.drawer_open),
            onClick = onMenu,
            tint = StarveilColors.ink,
        )
        Text(
            text = title,
            style = StarveilType.title,
            color = StarveilColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = StarveilSpacing.sm),
        )
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

/** The drawer: brand, pages, settings and diagnostics, about. */
@Composable
private fun StarveilDrawer(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        drawerContainerColor = StarveilColors.drawer,
        drawerContentColor = StarveilColors.ink,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            // The drawer header is the second and last surface allowed the
            // blooms; the sheet itself is opaque, so it draws its own.
            Box {
                PageWash(modifier = Modifier.matchParentSize())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(UiR.drawable.ic_starveil_mark),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = StarveilType.headline,
                        color = StarveilColors.ink,
                    )
                    Text(
                        text = stringResource(R.string.brand_tagline),
                        style = StarveilType.label.copy(fontSize = 12.sp),
                        color = StarveilColors.ink2,
                    )
                }
            }
            }

            DrawerGroup(stringResource(R.string.drawer_group_pages))
            DrawerRow(
                label = stringResource(R.string.nav_home),
                icon = StarveilIcons.Dashboard,
                selected = currentRoute == "dashboard",
                onClick = { onNavigate("home") },
            )
            DrawerRow(
                label = stringResource(R.string.nav_nodes),
                icon = StarveilIcons.Nodes,
                selected = currentRoute == "profiles",
                onClick = { onNavigate("nodes") },
            )
            DrawerRow(
                label = stringResource(R.string.nav_rules),
                icon = StarveilIcons.Route,
                selected = currentRoute == "rules",
                onClick = { onNavigate("rules_tab") },
            )
            DrawerRow(
                label = stringResource(R.string.nav_logs),
                icon = StarveilIcons.Terminal,
                selected = currentRoute == "logs",
                onClick = { onNavigate("logs_tab") },
            )

            DrawerGroup(stringResource(R.string.drawer_group_settings))
            DrawerRow(
                label = stringResource(R.string.nav_settings),
                icon = StarveilIcons.Settings,
                selected = currentRoute == "settings",
                onClick = { onNavigate("settings_tab") },
            )
            DrawerRow(
                label = stringResource(R.string.nav_scenes),
                icon = StarveilIcons.Route,
                selected = currentRoute == "scenes",
                onClick = { onNavigate("scenes_tab") },
            )
            DrawerRow(
                label = stringResource(R.string.nav_diagnostics),
                icon = StarveilIcons.Terminal,
                selected = currentRoute == "ruletest",
                onClick = { onNavigate("diagnostics") },
            )

            DrawerGroup(stringResource(R.string.drawer_group_about))
            DrawerRow(
                label = stringResource(R.string.drawer_github),
                icon = StarveilIcons.External,
                selected = false,
                onClick = { open(PROJECT_REPO) },
            )
            DrawerRow(
                label = stringResource(R.string.drawer_changelog),
                icon = StarveilIcons.External,
                selected = false,
                onClick = { open(PROJECT_CHANGELOG) },
            )
            DrawerRow(
                label = stringResource(R.string.drawer_issues),
                icon = StarveilIcons.External,
                selected = false,
                onClick = { open(PROJECT_ISSUES) },
            )
            Spacer(modifier = Modifier.height(StarveilSpacing.xl))
        }
    }
}

@Composable
private fun DrawerGroup(text: String) {
    Text(
        text = text,
        style = StarveilType.label.copy(fontWeight = FontWeight.SemiBold),
        color = StarveilColors.ink2,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

/**
 * One destination. 48dp, icon then label; the selected row is a pill with its
 * right corners rounded, flush to the drawer's left edge.
 */
@Composable
private fun DrawerRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(
                if (selected) {
                    Modifier
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                        .background(StarveilColors.selBg)
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) StarveilColors.selInk else StarveilColors.ink2,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = StarveilType.body.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) StarveilColors.selInk else StarveilColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val PROJECT_REPO = "https://github.com/justnpcx/Starveil"
private const val PROJECT_CHANGELOG = "https://github.com/justnpcx/Starveil/blob/main/CHANGELOG.md"
private const val PROJECT_ISSUES = "https://github.com/justnpcx/Starveil/issues"
