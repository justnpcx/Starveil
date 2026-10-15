package com.justnpcx.starveil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.data.preferences.isDark
import com.justnpcx.starveil.core.ui.theme.StarveilTheme
import com.justnpcx.starveil.ui.StarveilNavHost
import com.justnpcx.starveil.ui.Destination
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single Activity. All screens are Compose destinations inside
 * [StarveilNavHost]; this Activity owns only window + navigation chrome.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** The Activity is the composition root, so it reads the theme directly. */
    @Inject
    lateinit var preferences: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by preferences.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamicColor by preferences.dynamicColor
                .collectAsStateWithLifecycle(initialValue = false)

            StarveilTheme(
                darkTheme = themeMode.isDark(systemDark = isSystemInDarkTheme()),
                dynamicColor = dynamicColor,
            ) {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { StarveilBottomBar(navController) },
                ) { innerPadding ->
                    StarveilNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun StarveilBottomBar(navController: androidx.navigation.NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    NavigationBar {
        Destination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                // One line, always: a tab label that wraps (as "Dashboard" did)
                // drops its last letter onto a second row and grows the whole bar.
                label = {
                    Text(
                        text = destination.label,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
