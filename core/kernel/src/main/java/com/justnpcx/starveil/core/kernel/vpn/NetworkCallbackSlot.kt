package com.justnpcx.starveil.core.kernel.vpn

/**
 * Holds the one default-network callback this service registers.
 *
 * The platform keeps a strong reference to a registered callback, and that
 * reference reaches back into the service — a callback left registered after
 * the service is gone leaks the service, its binder and the tun it held.
 *
 * The bookkeeping lives here with the platform calls injected, so the rule can
 * be tested without a device: register once, release once, and never the other
 * way round. Both `onRevoke()` and `onDestroy()` release, which is only safe
 * because a second release is a no-op.
 */
internal class NetworkCallbackSlot(
    private val register: () -> Boolean,
    private val unregister: () -> Unit,
) {

    private var active = false

    val isActive: Boolean get() = active

    /** Returns false when a callback is already registered, or registration failed. */
    fun acquire(): Boolean {
        if (active) return false
        if (!register()) return false
        active = true
        return true
    }

    /** Returns false when there was nothing to release. */
    fun release(): Boolean {
        if (!active) return false
        // Cleared before the platform call: a callback that fires while it is
        // being unregistered must not see a slot that still claims to be live.
        active = false
        unregister()
        return true
    }
}
