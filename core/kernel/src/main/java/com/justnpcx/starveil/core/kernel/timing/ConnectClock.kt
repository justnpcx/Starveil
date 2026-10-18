package com.justnpcx.starveil.core.kernel.timing

/**
 * "The user just asked to connect."
 *
 * An interface rather than a singleton here: this module carries no DI
 * annotations on purpose, and the thing that owns the stopwatch needs the
 * kernel's flows, which live above it.
 */
fun interface ConnectClock {
    fun markConnectStart()
}
