package com.justnpcx.starveil.core.probe

import com.justnpcx.starveil.core.kernel.ConnectionEventKind
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.model.RuleHitDelta

/**
 * Counts routing decisions in memory, one per connection.
 *
 * Only a NEW event counts. UPDATE and CLOSE are the same connection speaking
 * again — counting them would turn one decision into a hundred for a long
 * download, and the number would then measure traffic, not usage.
 *
 * Deliberately not thread-safe by coroutine magic: every caller is the single
 * collector of the kernel's flow, and the flush reads it from another coroutine
 * only through [drain], which is synchronized. Keeping it lock-free inside
 * would buy nothing and hide the one real hazard.
 */
class RuleHitAccumulator(
    /** Distinct rules that trigger an early flush. */
    private val flushAt: Int = FLUSH_AT,
) {
    private val counts = LinkedHashMap<Pair<String, String>, Long>()
    private var events = 0L

    /**
     * Adds one event. Returns true when the buffer should be flushed now.
     *
     * A decision with no rule text is not counted: there is nothing to
     * attribute it to, and inventing a key ("", tag) would put a row in the
     * distribution that names no rule.
     */
    @Synchronized
    fun record(event: KernelConnection): Boolean {
        if (event.event != ConnectionEventKind.NEW) return false
        val rule = event.rule.trim()
        if (rule.isEmpty()) return false
        val key = rule to event.outbound
        counts[key] = (counts[key] ?: 0L) + 1L
        events += 1L
        return events >= flushAt
    }

    /** Takes everything accumulated so far; the buffer is empty afterwards. */
    @Synchronized
    fun drain(): List<RuleHitDelta> {
        if (counts.isEmpty()) return emptyList()
        val out = counts.map { (key, count) -> RuleHitDelta(key.first, key.second, count) }
        counts.clear()
        events = 0L
        return out
    }

    @get:Synchronized
    val pending: Int get() = counts.size

    companion object {
        /**
         * 100 events: often enough that a busy minute shows up while the user
         * is still on the page, rare enough that the write is not per
         * connection.
         */
        const val FLUSH_AT = 100
    }
}
