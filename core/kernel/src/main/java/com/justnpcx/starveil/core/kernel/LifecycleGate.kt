package com.justnpcx.starveil.core.kernel

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs kernel lifecycle transitions one at a time.
 *
 * The transitions share `server`, `client`, `_state` and `_blocking`, and each
 * one is a sequence of platform calls rather than a single assignment: a start
 * prepares the VpnService, opens the cache database and connects a client; a
 * stop unwinds all three. Two of them interleaving produces a second kernel
 * against a database the first still holds, which is the
 * `initialize cache-file: timeout` failure the teardown order exists to avoid.
 *
 * Waiting inside the gate is intended, including the seconds a restart spends
 * polling for the old VpnService: serialising is the point, and a transition
 * split in half to shorten the wait is no longer a transition.
 *
 * Read-only access — `state`, `blocking`, `logs`, `connections`, `hasTun` —
 * must stay outside the gate, or a subscriber waiting on a value would deadlock
 * the transition that is about to publish it.
 */
internal class LifecycleGate {

    private val mutex = Mutex()

    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }
}
