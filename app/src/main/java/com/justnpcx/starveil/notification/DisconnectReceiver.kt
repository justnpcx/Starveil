package com.justnpcx.starveil.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelDiagnostics
import com.justnpcx.starveil.core.kernel.KillSwitchController
import com.justnpcx.starveil.core.kernel.TrafficPolicy
import com.justnpcx.starveil.core.kernel.vpn.StarveilVpnService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The notification's Disconnect action.
 *
 * A receiver rather than a direct call because the code that can stop the
 * kernel lives in :app, while the notification lives with the service in
 * `:core:kernel`. The service knows the action name; this knows what to do with
 * it.
 *
 * **It asks the Kill Switch first.** Tearing the tunnel down unconditionally
 * would be the exact leak "block on disconnect" exists to prevent: whatever was
 * in flight would leave the device directly. The policy question is the same
 * one the dashboard asks — "once we are down, would traffic be allowed?" — so
 * it is answered by the same function rather than re-derived here.
 *
 * `goAsync` is what keeps the process alive until the stop finishes — a
 * receiver is otherwise killed the moment `onReceive` returns, and the tunnel
 * would survive a tap that looked like it worked.
 */
class DisconnectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StarveilVpnService.ACTION_DISCONNECT) return

        val pending = goAsync()
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DisconnectReceiverEntryPoint::class.java,
        )

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val keepTunnel = KillSwitchController.policyFor(
                    entry.preferences().killSwitchMode.first(),
                    connected = false,
                ) == TrafficPolicy.BLOCK
                val result = if (keepTunnel) {
                    entry.kernelBridge().stopKeepingTunnel()
                } else {
                    entry.kernelBridge().stop()
                }
                KernelDiagnostics.log(
                    "notification disconnect (keepTunnel=$keepTunnel): " +
                        (result.exceptionOrNull() ?: "ok"),
                )
            } finally {
                pending.finish()
            }
        }
    }
}

/** Hilt bridge: a receiver is not an injection target here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DisconnectReceiverEntryPoint {
    fun kernelBridge(): KernelBridge
    fun preferences(): PreferencesRepository
}
