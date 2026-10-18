package com.justnpcx.starveil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.data.preferences.isDark
import com.justnpcx.starveil.core.ui.theme.StarveilTheme
import com.justnpcx.starveil.core.probe.LeakCheckRunner
import com.justnpcx.starveil.core.probe.NodeHealthSampler
import com.justnpcx.starveil.core.probe.NodeLatencyTester
import com.justnpcx.starveil.onboarding.OnboardingScreen
import com.justnpcx.starveil.ui.StarveilNavHost
import com.justnpcx.starveil.ui.Destination
import com.justnpcx.starveil.core.ui.snackbar.UndoSnackbarHost
import com.justnpcx.starveil.ui.AppUndoBus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The single Activity. All screens are Compose destinations inside
 * [StarveilNavHost]; this Activity owns only window + navigation chrome.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** The Activity is the composition root, so it reads the theme directly. */
    @Inject
    lateinit var preferences: PreferencesRepository

    /**
     * The app's undo channel. The shell owns the Snackbar because it has to
     * outlive the screen that raised it (K2).
     */
    @Inject
    lateinit var undoBus: AppUndoBus

    /**
     * Keeps the node health curve fed. Started and stopped by this Activity's
     * lifecycle rather than by a scheduler: "foreground only" is the design
     * (nothing should wake the radio while the app is closed), and
     * [repeatOnLifecycle] is that policy expressed once.
     */
    @Inject
    lateinit var healthSampler: NodeHealthSampler

    /** The Nodes screen's latency button; injected here so the screen stays free of the kernel. */
    @Inject
    lateinit var latencyTester: NodeLatencyTester

    /** The leak self-check reads the VpnService, which only :app may touch. */
    @Inject
    lateinit var leakCheckRunner: LeakCheckRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Sampling stops when the app is not visible and resumes when it is.
        // The sampler loops until its coroutine is cancelled, so there is no
        // job held anywhere to forget to cancel.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                healthSampler.run()
            }
        }

        setContent {
            val themeMode by preferences.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamicColor by preferences.dynamicColor
                .collectAsStateWithLifecycle(initialValue = false)
            // null until the flag is read: showing the tabs first and then
            // replacing them with onboarding would flash the wrong screen.
            val onboardingDone by preferences.onboardingDone
                .collectAsStateWithLifecycle(initialValue = null)

            val dark = themeMode.isDark(systemDark = isSystemInDarkTheme())

            // The system bars follow the *app's* theme, not the system's.
            // `enableEdgeToEdge()` with no arguments decides the scrim from the
            // resource configuration, so forcing dark inside a light system left
            // a white navigation bar under a black app — the one thing in the
            // dark walkthrough that was plainly wrong rather than a taste call.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            StarveilTheme(
                darkTheme = dark,
                dynamicColor = dynamicColor,
            ) {
                when (onboardingDone) {
                    null -> Box(modifier = Modifier.fillMaxSize())
                    false -> OnboardingScreen()
                    true -> {
                        val navController = rememberNavController()
                        val snackbarHostState = remember { SnackbarHostState() }
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = { StarveilBottomBar(navController) },
                            snackbarHost = {
                                UndoSnackbarHost(requests = undoBus, hostState = snackbarHostState)
                            },
                        ) { innerPadding ->
                            StarveilNavHost(
                                navController = navController,
                                latencyTester = latencyTester,
                                leakCheckRunner = leakCheckRunner,
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                    }
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
                icon = { Icon(destination.icon, contentDescription = stringResource(destination.labelRes)) },
                // One line, always: a tab label that wraps (as "Dashboard" did)
                // drops its last letter onto a second row and grows the whole bar.
                label = {
                    Text(
                        text = stringResource(destination.labelRes),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
