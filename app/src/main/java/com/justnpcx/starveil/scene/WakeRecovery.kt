package com.justnpcx.starveil.scene

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.justnpcx.starveil.core.data.network.ExitProbe
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelDiagnostics
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.feature.dashboard.ConnectionController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Puts the tunnel back when the phone wakes up and finds it dead.
 *
 * A screen that was off for a while is the one case the kernel cannot see:
 * its state still says Running, the config is still on disk, and every socket
 * underneath it has been closed by the radio going to sleep. The user unlocks,
 * the icon says connected, and nothing loads — the same lie [TrafficWatch]
 * was built for, but here the app is not even on screen to say it.
 *
 * The test is the exit probe, twice: a phone that just woke is still bringing
 * its radio up, and one failed request in that first second is normal. Two
 * failures a few seconds apart are a dead tunnel, and the answer is the same
 * rebuild every other path uses ([ConnectionController.reapply]).
 *
 * Off by [PreferencesRepository.autoReconnect]: someone who turned automatic
 * reconnection off does not want the app starting tunnels behind their back.
 */
@Singleton
class WakeRecovery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kernel: KernelBridge,
    private val connection: ConnectionController,
    private val exitProbe: ExitProbe,
    private val preferences: PreferencesRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var receiver: BroadcastReceiver? = null
    private var check: Job? = null

    /** Registers the receiver. Idempotent. */
    fun start() {
        if (receiver != null) return
        val registered = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = schedule()
        }
        // Runtime registration, not a manifest entry: SCREEN_ON is one of the
        // actions the system refuses to deliver to a manifest receiver.
        ContextCompat.registerReceiver(
            context,
            registered,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiver = registered
    }

    private fun schedule() {
        // Both actions fire on one unlock, and a phone can fire SCREEN_ON
        // repeatedly while a notification wakes it. One check at a time.
        if (check?.isActive == true) return
        check = scope.launch {
            // Nothing to check when nothing is up. Probing anyway would answer
            // about the physical network and read as a healthy tunnel.
            if (!isForwarding()) return@launch
            delay(FIRST_ATTEMPT_DELAY_MS)
            val first = exitProbe.probe()
            if (first != null) {
                // One line per unlock, so "did the check run" is answerable from
                // the log rather than by trusting the absence of a failure.
                KernelDiagnostics.log("wake: exit ${first.address} in ${first.rttMs} ms, tunnel kept")
                return@launch
            }
            delay(RETRY_DELAY_MS)
            val second = exitProbe.probe()
            if (second != null) {
                KernelDiagnostics.log("wake: exit ${second.address} in ${second.rttMs} ms, tunnel kept")
                return@launch
            }
            val running = isForwarding()
            val allowed = preferences.autoReconnect.first()
            if (!shouldRebuildAfterWake(first != null, second != null, running, allowed)) {
                KernelDiagnostics.log("wake: exit unreachable, not rebuilding (running=$running, auto-reconnect=$allowed)")
                return@launch
            }

            KernelDiagnostics.log("wake: exit unreachable, rebuilding the tunnel")
            connection.reapply()
            val after = exitProbe.probe()
            KernelDiagnostics.log(
                if (after == null) {
                    "wake: still unreachable after the rebuild"
                } else {
                    "wake: back, exit ${after.address} in ${after.rttMs} ms"
                },
            )
        }
    }

    /**
     * A tunnel that carries traffic: running, and not the Kill Switch's
     * block-only config. Blocking on purpose is not a tunnel to repair — the
     * probe fails by design there, and rebuilding would fight the setting.
     */
    private fun isForwarding(): Boolean =
        kernel.state.value == KernelState.Running && !kernel.blocking.value

    private companion object {
        /** Long enough for the radio to come back after a doze. */
        const val FIRST_ATTEMPT_DELAY_MS = 1_500L

        /** Long enough that the second answer is about the tunnel, not the wake. */
        const val RETRY_DELAY_MS = 3_000L
    }
}

/**
 * Whether a wake that found no exit should rebuild the tunnel.
 *
 * Its own function because it is the whole policy, and because the interesting
 * cases are the refusals: a tunnel that is not running has nothing to rebuild,
 * and a user who turned automatic reconnection off did not ask for one.
 */
internal fun shouldRebuildAfterWake(
    firstProbeOk: Boolean,
    secondProbeOk: Boolean,
    kernelRunning: Boolean,
    autoReconnect: Boolean,
): Boolean = !firstProbeOk && !secondProbeOk && kernelRunning && autoReconnect
