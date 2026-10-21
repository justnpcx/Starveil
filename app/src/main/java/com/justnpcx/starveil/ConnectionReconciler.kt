package com.justnpcx.starveil

import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes the process's idea of the connection agree with the phone's.
 *
 * The kernel's state is in memory, so a new process starts believing nothing is
 * connected — but the tun is not in memory. It belongs to the VpnService, the
 * system keeps it across a process death (`START_STICKY`, and always-on VPN
 * restarts it at boot), and while it is up every app on the phone is routed
 * into it. Nothing reads it, because the kernel that would have is gone.
 *
 * That is the worst of the two possible lies: the phone has no working network,
 * and the app says "not connected" and offers to connect. Connecting then fails
 * on the cache database the dead daemon still holds — which is the
 * `initialize cache-file: timeout` this class is meant to keep out of reach.
 *
 * So at start-up: a tunnel with no kernel behind it is taken down, and the
 * screen is told the truth. A tunnel *with* a kernel is left alone — that is a
 * live connection, and this process being new is not a reason to break it.
 */
@Singleton
class ConnectionReconciler @Inject constructor(
    private val kernel: KernelBridge,
) {

    /**
     * Reconcile once. Returns what it did, for the log and for tests.
     *
     * Idempotent: the second call finds no tunnel and does nothing.
     */
    suspend fun run(): Outcome {
        val tunnel = kernel.tunnelPresent()
        val state = kernel.state.value

        // No tunnel and no kernel: the ordinary "nothing is connected" case.
        if (!tunnel && state !is KernelState.Running) return Outcome.Consistent

        // Both present: a real connection, whatever else is true.
        if (tunnel && state is KernelState.Running) return Outcome.Connected

        // A tunnel with no kernel: the orphan. Release it, which also stops the
        // service, so the VPN icon goes away and traffic leaves directly again.
        if (tunnel) {
            // The bridge logs the release itself, with the level and the tag
            // the Logs screen expects — one line per event, not two.
            return if (kernel.releaseOrphanTunnel()) {
                Outcome.OrphanReleased
            } else {
                Outcome.Consistent
            }
        }

        // A kernel with no tunnel: it cannot be forwarding anything, and the
        // next connect would try to start a second one. Stop it.
        kernel.stop()
        return Outcome.KernelWithoutTunnel
    }

    /**
     * Whether the connection this process lost should be brought back.
     *
     * Separate from [run] because it is a different question: [run] is about
     * making the screen honest, this is about what the user asked for. The two
     * preferences are passed in rather than read here, so this stays a decision
     * about an outcome and the caller owns what "the user asked for" means —
     * the same two keys the drop-recovery path reads, so a restart behaves like
     * a drop rather than like something the app decided on its own.
     */
    fun shouldRestore(outcome: Outcome, autoReconnect: Boolean, hasTarget: Boolean): Boolean {
        // Only the case where a connection was lost: a fresh process with no
        // tunnel has nothing to restore, and a live tunnel is already up.
        if (outcome != Outcome.OrphanReleased) return false
        return autoReconnect && hasTarget
    }

    enum class Outcome {
        /** Nothing was connected, or it already agreed. */
        Consistent,

        /** A tunnel and a kernel: a live connection. Left untouched. */
        Connected,

        /** A tunnel with nothing behind it, now closed. */
        OrphanReleased,

        /** A kernel with no tunnel, now stopped. */
        KernelWithoutTunnel,
    }
}