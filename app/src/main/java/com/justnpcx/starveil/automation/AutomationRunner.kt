package com.justnpcx.starveil.automation

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.justnpcx.starveil.MainActivity
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.feature.dashboard.ConnectionController
import com.justnpcx.starveil.feature.dashboard.ConnectionOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The three external actions, executed off the caller's lifecycle.
 *
 * The activity that receives the intent finishes immediately, so the work
 * cannot live in its scope: an automation call must survive the caller (Tasker,
 * MacroDroid, adb) walking away the moment the intent is delivered. A process
 * scope plus a mutex also gives idempotence for free — two TOGGLEs arriving
 * together run one after the other, each reading the state the previous one
 * left, instead of both seeing "running" and both stopping.
 */
@Singleton
class AutomationRunner @Inject constructor(
    private val controller: ConnectionController,
    private val kernel: KernelBridge,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = Mutex()

    fun dispatch(action: String?) {
        val intent = action ?: return
        scope.launch {
            gate.withLock {
                when (intent) {
                    ACTION_START -> start()
                    ACTION_STOP -> stop()
                    ACTION_TOGGLE -> toggle()
                    else -> Log.w(TAG, "unknown action: $intent")
                }
            }
        }
    }

    private suspend fun start() {
        if (kernel.state.value == KernelState.Running) {
            Log.i(TAG, "START: already connected")
            return
        }
        // The platform owns VPN consent. connect() would fail with a refusal
        // that reads like a bug; asking here lets the app show its own consent
        // flow instead, which is the only thing the user can act on.
        if (VpnService.prepare(context) != null) {
            Log.i(TAG, "START: VPN permission not granted; opening the app")
            openApp()
            return
        }
        when (val outcome = controller.connect()) {
            is ConnectionOutcome.Connected -> Log.i(TAG, "START: connected")
            // Nothing selected, an unbuildable config, or a refusal: all of
            // them need a screen, and a silent no-op would look like the
            // automation did not fire.
            is ConnectionOutcome.NoTarget,
            is ConnectionOutcome.ConfigUnbuildable,
            is ConnectionOutcome.Refused,
            -> {
                Log.i(TAG, "START: needs attention ($outcome); opening the app")
                openApp()
            }
            else -> Log.i(TAG, "START: $outcome")
        }
    }

    private suspend fun stop() {
        if (kernel.state.value != KernelState.Running) {
            Log.i(TAG, "STOP: not connected")
            return
        }
        Log.i(TAG, "STOP: ${controller.disconnect()}")
    }

    private suspend fun toggle() {
        if (kernel.state.value == KernelState.Running) stop() else start()
    }

    private fun openApp() {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    companion object {
        private const val TAG = "StarveilAutomation"

        const val ACTION_START = "com.justnpcx.starveil.action.START"
        const val ACTION_STOP = "com.justnpcx.starveil.action.STOP"
        const val ACTION_TOGGLE = "com.justnpcx.starveil.action.TOGGLE"
    }
}
