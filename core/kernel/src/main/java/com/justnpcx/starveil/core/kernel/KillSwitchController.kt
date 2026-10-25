package com.justnpcx.starveil.core.kernel

import com.justnpcx.starveil.core.model.KillSwitchMode

/** Whether the Kill Switch permits traffic right now. */
enum class TrafficPolicy { ALLOW, BLOCK }

/**
 * Pure Kill Switch decision (PRD §12 Q3).
 *
 * Kept as a pure function — no kernel, no IO — so the three modes are
 * unit-testable now, while the actual enforcement lands with RealKernelBridge
 * (task 1) and is verified in the task-7 smoke.
 */
object KillSwitchController {

    fun policyFor(mode: KillSwitchMode, connected: Boolean): TrafficPolicy =
        when (mode) {
            KillSwitchMode.OFF -> TrafficPolicy.ALLOW
            KillSwitchMode.BLOCK_ON_DISCONNECT ->
                if (connected) TrafficPolicy.ALLOW else TrafficPolicy.BLOCK

            KillSwitchMode.ALWAYS_BLOCK -> TrafficPolicy.BLOCK
        }

    /**
     * Whether starting the app should put the block in place.
     *
     * The policy outlives a process; the kernel does not. A phone whose app was
     * killed sits there with "block on disconnect" chosen and traffic flowing —
     * the one promise that mode makes, broken by something the user did not do.
     *
     * [kernelRunning] is the whole check on the other side: a tunnel that is up
     * (forwarding, or already blocking) needs nothing from here, and blocking
     * again would tear down a working connection.
     */
    fun shouldBlockOnStart(mode: KillSwitchMode, kernelRunning: Boolean): Boolean =
        !kernelRunning && policyFor(mode, connected = false) == TrafficPolicy.BLOCK
}

/** Leak self-check (PRD §3.6 K9). */
object LeakCheck {

    /**
     * A leak is: the Kill Switch says "block", but traffic is still allowed.
     * [connected] is the kernel state; [trafficAllowed] is the observed result.
     */
    fun isLeaking(mode: KillSwitchMode, connected: Boolean, trafficAllowed: Boolean): Boolean =
        KillSwitchController.policyFor(mode, connected) == TrafficPolicy.BLOCK && trafficAllowed
}
