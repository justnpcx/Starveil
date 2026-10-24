package com.justnpcx.starveil

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import com.justnpcx.starveil.core.ui.theme.StarveilType
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import com.justnpcx.starveil.core.ui.components.StarveilBackground
import com.justnpcx.starveil.core.ui.theme.StarveilTheme
import com.justnpcx.starveil.core.kernel.KernelDiagnostics
import com.justnpcx.starveil.core.probe.LeakCheckRunner
import com.justnpcx.starveil.core.probe.NodeHealthSampler
import com.justnpcx.starveil.core.probe.NodeLatencyTester
import com.justnpcx.starveil.deeplink.DeepLinkInbox
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

    /** Holds a share link until a screen can import it. */
    @Inject
    lateinit var deepLinks: DeepLinkInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A share link that launched the app. `savedInstanceState == null` is
        // the difference between a fresh launch and a recreation: on a
        // recreation the intent is the same one, and the inbox is what stops it
        // being imported twice, but not re-offering it here keeps the intent
        // from being re-read at all.
        if (savedInstanceState == null) offerDeepLink(intent)

        // Sampling stops when the app is not visible and resumes when it is.
        // The sampler loops until its coroutine is cancelled, so there is no
        // job held anywhere to forget to cancel.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                healthSampler.run()
            }
        }

        setContent {
            // null until the flag is read: showing the tabs first and then
            // replacing them with onboarding would flash the wrong screen.
            val onboardingDone by preferences.onboardingDone
                .collectAsStateWithLifecycle(initialValue = null)

            // One theme, and it is light: both system bars are transparent over
            // the page, with dark icons. `auto` would follow the system and
            // leave dark icons on a dark bar for anyone who runs their phone in
            // dark mode, which this app does not.
            LaunchedEffect(Unit) {
                val style = SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                )
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            StarveilTheme {
                when (onboardingDone) {
                    null -> Box(modifier = Modifier.fillMaxSize())
                    false -> OnboardingScreen()
                    true -> {
                        val navController = rememberNavController()
                        val snackbarHostState = remember { SnackbarHostState() }
                        StarveilBackground {
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                // Transparent: the ambient gradient is painted
                                // once behind this Scaffold, and a container
                                // colour here would cover it.
                                containerColor = Color.Transparent,
                                // The bottom bar owns the navigation inset (it
                                // pads itself), so the content must not also be
                                // inset by it: Scaffold would add both, and
                                // every floating button ended up ~40dp higher
                                // than its 16dp margin.
                                contentWindowInsets = WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                                ),
                                bottomBar = { StarveilBottomBar(navController) },
                                snackbarHost = {
                                    UndoSnackbarHost(
                                        requests = undoBus,
                                        hostState = snackbarHostState,
                                    )
                                },
                            ) { innerPadding ->
                                StarveilNavHost(
                                    navController = navController,
                                    latencyTester = latencyTester,
                                    leakCheckRunner = leakCheckRunner,
                                    deepLinks = deepLinks,
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The app was already running when the link arrived.
     *
     * `singleTask` in the manifest is what makes this happen instead of a second
     * copy of the app being started; without it the tap would open a second
     * Activity whose tunnel belongs to the first.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Replaces the Activity's intent, so a recreation sees this one rather
        // than the launch intent from before the tap.
        setIntent(intent)
        offerDeepLink(intent)
    }

    /**
     * Hand a `VIEW` intent's link to the inbox.
     *
     * `dataString` rather than `data.toString()`: the latter normalises the
     * URI, and a `vmess://` body is base64 that a normalising round trip can
     * alter — the parser would then fail on a link that was fine.
     *
     * The link itself is never logged. It carries the node's credentials, and
     * the whole point of the redaction rule is that those do not reach a log.
     */
    private fun offerDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val accepted = deepLinks.offer(intent.dataString)
        if (!accepted && intent.dataString != null) {
            KernelDiagnostics.log("ignored a link this app cannot parse")
        }
    }
}

@Composable
private fun StarveilBottomBar(navController: androidx.navigation.NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val hairline = StarveilColors.hairline
    val accent = StarveilColors.accent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The bar is drawn edge to edge, so it has to clear the system
            // navigation inset itself: without this the labels sit under the
            // gesture handle and a tap in that strip goes to the system.
            .navigationBarsPadding()
            // The page colour, then the chrome material over it. The pair is
            // the degraded form §5 allows: Compose has no backdrop blur, so a
            // translucent bar over scrolling text would smear rather than
            // frost. Two paints give the same slightly-lifted surface with the
            // text kept out of it.
            .background(StarveilColors.bg)
            .background(StarveilColors.surfaceChrome)
            .drawBehind {
                // The bar's only separation from the page is this line. A
                // shadow would be the one soft edge in an app that has none.
                drawLine(
                    color = hairline,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Destination.entries.forEach { destination ->
                val selected =
                    currentDestination?.hierarchy?.any { it.route == destination.route } == true
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        // The platform's minimum, so a tab is a target rather
                        // than a label.
                        .heightIn(min = 56.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(vertical = StarveilSpacing.sm),
                ) {
                    // The dot is the selection cue, and it is cyan: the one
                    // place the accent appears in the chrome. It sits above the
                    // glyph and holds its slot when unselected, so neither the
                    // icon nor the label moves between tabs.
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (selected) accent else Color.Transparent),
                    )
                    Spacer(modifier = Modifier.height(StarveilSpacing.xs))
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                        tint = if (selected) StarveilColors.ink else StarveilColors.ink3,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.height(StarveilSpacing.xs))
                    Text(
                        text = stringResource(destination.labelRes),
                        style = StarveilType.tech,
                        color = if (selected) StarveilColors.ink else StarveilColors.ink3,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
