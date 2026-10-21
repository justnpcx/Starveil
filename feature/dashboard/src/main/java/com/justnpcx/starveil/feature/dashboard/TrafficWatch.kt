package com.justnpcx.starveil.feature.dashboard

/**
 * Watches the one thing the kernel's state cannot say: whether anything is
 * actually going through.
 *
 * "Connected" means the tunnel exists and the config was accepted. It does not
 * mean a packet reached the other side — the node can be down, the credential
 * can be wrong, the route can be sending everything to a server that drops it.
 * In all three cases the tunnel is up and the phone has no internet, which is
 * the worst state to be in silently: the icon says connected, so nothing looks
 * wrong.
 *
 * The signal is the first connection the kernel decides to *proxy*. It costs
 * nothing — the kernel already reports its routing decisions — and it is
 * stronger than a ping, which would measure the tunnel and not the node.
 *
 * The countdown only runs while the screen is watching and the tunnel is up. A
 * backgrounded app that has not sent a packet is not a failure, and treating it
 * as one would cry wolf every time the phone sat idle.
 */
class TrafficWatch {

    /** What the watch concluded, if anything. */
    enum class Verdict {
        /** Still waiting; nothing to say yet. */
        Waiting,

        /** A proxied connection arrived. The tunnel carries traffic. */
        Carrying,

        /** The window closed with nothing proxied. The tunnel may be a dead end. */
        Silent,
    }

    private var armedAtMs: Long? = null
    private var verdict = Verdict.Waiting

    /** Start a countdown. Called when a connect is issued. */
    fun arm(nowMs: Long) {
        armedAtMs = nowMs
        verdict = Verdict.Waiting
    }

    /** Forget everything: the tunnel went down, or the user is not looking. */
    fun reset() {
        armedAtMs = null
        verdict = Verdict.Waiting
    }

    /** The kernel proxied something. */
    fun onProxied(nowMs: Long): Verdict {
        // Only the first one after arming matters: later traffic is business as
        // usual, and re-arming on every connection would keep the state at
        // "carrying" forever even after it stopped.
        if (armedAtMs == null || verdict != Verdict.Waiting) return verdict
        armedAtMs = null
        verdict = Verdict.Carrying
        return verdict
    }

    /**
     * Has the window closed with nothing proxied?
     *
     * [nowMs] is a parameter rather than read inside, so the boundary is a test
     * rather than a wait.
     */
    fun check(nowMs: Long): Verdict {
        val armed = armedAtMs ?: return verdict
        if (verdict != Verdict.Waiting) return verdict
        if (nowMs - armed < WINDOW_MS) return Verdict.Waiting
        armedAtMs = null
        verdict = Verdict.Silent
        return verdict
    }

    /** Whether a countdown is running, so the caller knows to keep polling. */
    val isArmed: Boolean get() = armedAtMs != null

    companion object {
        /**
         * How long a fresh tunnel gets before it is called silent.
         *
         * Long enough for a slow node's handshake and the app's first request
         * (a browser opens several); short enough that a user who just tapped
         * connect is still looking at the screen when the answer arrives.
         */
        const val WINDOW_MS = 10_000L
    }
}