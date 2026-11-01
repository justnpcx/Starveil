package com.justnpcx.starveil.core.data.ruleset

/**
 * The periodic rule-set check, throttled by a timestamp.
 *
 * Runs on app start and on tunnel start, and does nothing almost every time:
 * the upstream publishes when it publishes, and a check every launch would be
 * a request per connect for a file that changes weekly at most.
 *
 * The timestamp is written whatever the outcome — a check that failed must not
 * be retried on the next launch either, or an unreachable mirror turns into a
 * request per app start. The next attempt is a week later, silently.
 */
internal class RuleSetAutoCheck(
    private val updater: RuleSetUpdater,
    private val lastCheckedAt: suspend () -> Long,
    private val recordCheckedAt: suspend (Long) -> Unit,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val intervalMs: Long = SEVEN_DAYS_MS,
    private val warn: (String) -> Unit = {},
) {

    /** The check's result, or null when it was throttled. */
    suspend fun runIfDue(): RuleSetUpdater.Result? {
        val now = nowMs()
        val last = lastCheckedAt()
        if (last > 0L && now - last < intervalMs) return null

        // Recorded before the work, not after: a process killed mid-check would
        // otherwise come back and start the same fetch again.
        recordCheckedAt(now)
        val result = updater.check()
        if (result is RuleSetUpdater.Result.Failed) {
            warn("rule sets: check failed (" + result.reason + "); next attempt in a week")
        }
        return result
    }

    /** A manual check: the throttle does not apply, and the timestamp moves. */
    suspend fun runNow(): RuleSetUpdater.Result {
        val now = nowMs()
        recordCheckedAt(now)
        return updater.check()
    }

    companion object {
        const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}
