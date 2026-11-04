package com.justnpcx.starveil.rulehits

import com.justnpcx.starveil.core.data.repository.RuleHitRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.probe.RuleHitAccumulator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns the kernel's connection events into persisted rule hit counts.
 *
 * The event callback only touches memory: a busy tunnel emits an event per
 * connection and per second per open connection, and a database write in that
 * callback would put SQLite in the path of the kernel's own reporting. The
 * buffer is flushed every [FLUSH_MS] or as soon as [RuleHitAccumulator] says it
 * has [RuleHitAccumulator.FLUSH_AT] events, whichever comes first.
 *
 * Scope of the count, stated where it is produced: only connections this app
 * observed through the kernel's event stream. While the tunnel runs that is
 * every connection it carried; while the process is gone there are no events,
 * and therefore no counts. The diagnostics page says the same thing.
 */
@Singleton
class RuleHitRecorder @Inject constructor(
    private val bridge: KernelBridge,
    private val repository: RuleHitRepository,
) {
    private val accumulator = RuleHitAccumulator()
    private val flushLock = Mutex()
    private var started = false

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch {
            bridge.connections.collect { event ->
                if (accumulator.record(event)) flush()
            }
        }
        scope.launch {
            while (isActive) {
                delay(FLUSH_MS)
                flush()
            }
        }
    }

    /**
     * Writes whatever is buffered.
     *
     * Failures are swallowed on purpose: the counters are a diagnostic, and a
     * locked database must not take the collector down with it. The numbers
     * that were buffered are lost, which is the least-bad outcome and is
     * visible as a gap rather than a wrong attribution.
     */
    suspend fun flush() {
        flushLock.withLock {
            val deltas = accumulator.drain()
            if (deltas.isEmpty()) return
            runCatching { repository.record(deltas, System.currentTimeMillis()) }
        }
    }

    companion object {
        /** 30 s: often enough to see a hit appear while looking at the page. */
        const val FLUSH_MS = 30_000L
    }
}
