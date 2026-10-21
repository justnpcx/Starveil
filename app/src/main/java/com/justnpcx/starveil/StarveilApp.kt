package com.justnpcx.starveil

import android.app.Application
import com.justnpcx.starveil.core.data.crash.CrashReportingSwitch
import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.crash.CrashReporting
import com.justnpcx.starveil.core.probe.NodeHealthProbeScheduler
import com.justnpcx.starveil.rules.RuleSetFailureReporter
import com.justnpcx.starveil.feature.dashboard.ConnectionController
import com.justnpcx.starveil.scene.SceneController
import com.justnpcx.starveil.timing.ConnectionTiming
import com.justnpcx.starveil.widget.StarveilWidget
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application entry point. Hilt graph root.
 *
 * [onCreate] stays free of heavy work: besides loading the native database
 * library, the only thing here is the widget's state feed, which runs on a
 * background scope and never blocks the first frame.
 */
@HiltAndroidApp
class StarveilApp : Application() {

    @Inject
    lateinit var kernel: KernelBridge

    @Inject
    lateinit var crashReporting: CrashReportingSwitch

    @Inject
    lateinit var healthProbe: NodeHealthProbeScheduler

    @Inject
    lateinit var ruleSetFailures: RuleSetFailureReporter

    @Inject
    lateinit var scenes: SceneController

    @Inject
    lateinit var connectionTiming: ConnectionTiming

    /** Makes this process's idea of the connection agree with the phone's. */
    @Inject
    lateinit var connectionReconciler: ConnectionReconciler

    /** The connect path, used only to restore a connection this process lost. */
    @Inject
    lateinit var connection: ConnectionController

    @Inject
    lateinit var preferences: PreferencesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Before anything else can throw. Inert when the build has no DSN.
        CrashReporting.install(this, crashReporting)

        // SQLCipher is a native library and must be loaded before the first
        // database access; the Room instance is built lazily on first injection.
        System.loadLibrary("sqlcipher")

        // A widget is RemoteViews rendered by the launcher — it cannot observe
        // anything, so the process that owns the state has to push. That works
        // because while the tunnel is up this process is alive by definition:
        // the VPN service runs in it.
        scope.launch {
            kernel.state.collect { StarveilWidget().updateAll(this@StarveilApp) }
        }

        // Scheduled on every start, not once at install: the request lives in
        // WorkManager's store, and re-enqueueing it is how a changed interval
        // or constraint reaches an existing install.
        healthProbe.schedule(scope)

        // A rejected rule set is the one failure that otherwise looks like the
        // app quietly ignoring a setting; it belongs in the log the user can
        // actually read.
        ruleSetFailures.start(scope)

        // Before scenes: they evaluate the network, and a tunnel left over from
        // a previous process would be read as a live connection.
        //
        // The process can die while the system keeps the VpnService, which
        // leaves a tunnel nobody reads: every app offline, and the UI — which
        // reads the kernel's in-memory state — saying "not connected". Release
        // it, then bring the connection back if that is what the user asked
        // for, so a reboot with always-on VPN looks like a reconnect rather
        // than like a phone with no network.
        scope.launch {
            val outcome = connectionReconciler.run()
            val autoReconnect = preferences.autoReconnect.first()
            val hasTarget = preferences.connectionTarget.first() != ConnectionTarget.None
            if (connectionReconciler.shouldRestore(outcome, autoReconnect, hasTarget)) {
                connection.connect()
            }
        }

        // Scenes are evaluated while the process lives, which is exactly while
        // the tunnel is up: the VpnService keeps it alive. With no process
        // there is no tunnel to switch, so nothing is lost.
        scenes.start()

        // Two lines per connect in the Logs screen: how long the tunnel took to
        // come up, and how long until traffic actually went through it.
        connectionTiming.start(scope)
    }
}
