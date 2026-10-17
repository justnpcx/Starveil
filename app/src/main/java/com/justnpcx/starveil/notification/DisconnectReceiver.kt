package com.justnpcx.starveil.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.justnpcx.starveil.core.kernel.KernelDiagnostics
import com.justnpcx.starveil.core.kernel.vpn.StarveilVpnService
import com.justnpcx.starveil.feature.dashboard.ConnectionController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The notification's Disconnect action.
 *
 * A receiver rather than a direct call because the code that can stop the
 * kernel lives in :app, while the notification lives with the service in
 * `:core:kernel`. The service knows the action name; this knows what to do with
 * it.
 *
 * **It goes through the same controller the dashboard uses.** The Kill Switch
 * decision, and the block itself, are one path: duplicating "ask the policy,
 * then block or stop" here is how the two would drift, and a drift in this
 * particular decision is a leak.
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
                KernelDiagnostics.log("notification disconnect: ${entry.connection().disconnect()}")
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
    fun connection(): ConnectionController
}
