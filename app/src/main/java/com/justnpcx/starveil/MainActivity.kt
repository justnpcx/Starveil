package com.justnpcx.starveil

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.key
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
import com.justnpcx.starveil.core.model.AccentPreset
import com.justnpcx.starveil.core.model.FontScale
import com.justnpcx.starveil.core.model.ThemeMode
import com.justnpcx.starveil.core.ui.components.StarveilBackground
import com.justnpcx.starveil.core.ui.theme.StarveilTheme
import com.justnpcx.starveil.core.kernel.KernelDiagnostics
import com.justnpcx.starveil.core.probe.LeakCheckRunner
import com.justnpcx.starveil.core.probe.NodeHealthSampler
import com.justnpcx.starveil.core.probe.NodeLatencyTester
import com.justnpcx.starveil.deeplink.DeepLinkInbox
import com.justnpcx.starveil.ui.StarveilNavHost
import com.justnpcx.starveil.ui.StarveilShell
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
class MainActivity : AppCompatActivity() {

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
            val themeMode by preferences.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val accentPreset by preferences.accentPreset
                .collectAsStateWithLifecycle(initialValue = AccentPreset.STAR_BLUE)
            val fontScale by preferences.fontScale
                .collectAsStateWithLifecycle(initialValue = FontScale.STANDARD)
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Both bars are transparent over the page. The icon grade flips with
            // the palette: dark glyphs on the light page, light glyphs on the
            // dark one. Leaving it fixed is how a dark theme ends up with
            // invisible status icons.
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    )
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            // Text size is a density multiplier, and a density only lands on
            // the next measure: what is already on screen has laid its text
            // out, and a changed local does not by itself re-measure it. Keying
            // the tree on the choice rebuilds it, which is what the system does
            // when its own font size changes. The nav controller and the
            // snackbar sit above the key, so the page you were on is still the
            // page you are on after the switch.
            val navController = rememberNavController()
            val snackbarHostState = remember { SnackbarHostState() }
            key(fontScale) {
                StarveilTheme(darkTheme = dark, accent = accentPreset, fontScale = fontScale) {
                    StarveilShell(
                        navController = navController,
                        darkTheme = dark,
                        onToggleTheme = {
                            // From "follow system", the button pins the
                            // opposite of what is on screen. From a pin, it
                            // flips the pin.
                            val next = if (dark) ThemeMode.LIGHT else ThemeMode.DARK
                            lifecycleScope.launch { preferences.setThemeMode(next) }
                        },
                        snackbarHost = {
                            UndoSnackbarHost(
                                requests = undoBus,
                                hostState = snackbarHostState,
                            )
                        },
                    ) {
                        // The shell applies its own bar insets to the content; a
                        // second padding here would be the bars counted twice.
                        StarveilNavHost(
                            navController = navController,
                            latencyTester = latencyTester,
                            leakCheckRunner = leakCheckRunner,
                            deepLinks = deepLinks,
                            modifier = Modifier.fillMaxSize(),
                        )
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
