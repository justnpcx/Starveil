package com.justnpcx.starveil.scene

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Wait until things stop changing, then do it once."
 *
 * A phone moving between cells fires several network callbacks in a row, and a
 * scene applied on each one would rebuild the tunnel for a single real change.
 * Each submission cancels the previous wait, so only the last one runs — with
 * the state as it is *then*, not as it was at the first event.
 */
internal class Debouncer(
    private val scope: CoroutineScope,
    private val windowMs: Long,
) {
    private var pending: Job? = null

    fun submit(action: suspend () -> Unit) {
        pending?.cancel()
        pending = scope.launch {
            delay(windowMs)
            action()
        }
    }
}
