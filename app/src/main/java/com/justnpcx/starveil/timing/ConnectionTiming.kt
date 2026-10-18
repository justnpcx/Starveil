package com.justnpcx.starveil.timing

import android.os.SystemClock
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.kernel.KernelDiagnostics
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.timing.ConnectClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * How long it takes from "the user asked to connect" to "traffic is going
 * through the proxy".
 *
 * Two marks, both logged as one line each:
 *
 *   kernel up in <ms>   the tunnel exists and the config was accepted
 *   TTFP <ms> via <type>  the first connection the kernel decided to proxy
 *
 * TTFP is the number that matters and the one that is not obvious: the kernel
 * can be "running" for a while before anything is asked of it. It is measured
 * from the connect call, so it includes building the config and starting the
 * kernel — everything the app does before the network does its part.
 *
 * The mark is armed by [markConnectStart] and disarmed by the first proxied
 * connection, so a later connection does not produce a second reading.
 * `direct` and `block` do not count: neither is traffic going through the
 * proxy.
 */
@Singleton
class ConnectionTiming @Inject constructor(
    private val kernel: KernelBridge,
) : ConnectClock {

    @Volatile
    private var connectStartedAt = 0L

    @Volatile
    private var kernelUpLogged = false

    override fun markConnectStart() {
        connectStartedAt = SystemClock.elapsedRealtime()
        kernelUpLogged = false
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            kernel.state.collect { state ->
                if (state !is KernelState.Running) return@collect
                val started = connectStartedAt
                if (started == 0L || kernelUpLogged) return@collect
                kernelUpLogged = true
                KernelDiagnostics.log("kernel up in ${SystemClock.elapsedRealtime() - started} ms")
            }
        }
        scope.launch {
            kernel.connections.collect { connection ->
                val started = connectStartedAt
                if (started == 0L || !connection.isProxied()) return@collect
                connectStartedAt = 0L
                KernelDiagnostics.log(
                    "TTFP ${SystemClock.elapsedRealtime() - started} ms via ${connection.outboundType}",
                )
            }
        }
    }

    private fun KernelConnection.isProxied(): Boolean =
        outboundType.isNotBlank() &&
            outboundType != "direct" &&
            outboundType != "block"
}
