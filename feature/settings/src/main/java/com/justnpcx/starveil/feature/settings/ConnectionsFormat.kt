package com.justnpcx.starveil.feature.settings

/**
 * How the connections table renders a number.
 *
 * Apart from the screen because the interesting cases cannot be produced on a
 * device: a connection that has been open for two days, or one whose start the
 * kernel never reported. Both are one function call away in a test, and neither
 * is reachable by waiting.
 */
internal object ConnectionsFormat {

    /**
     * A duration in the largest unit that still says something.
     *
     * Seconds, then minutes and seconds, then hours and minutes, then days. A
     * null duration is a dash: the row exists, the start does not.
     */
    fun duration(ms: Long?): String = when {
        ms == null -> "—"
        ms < 1_000 -> "${ms}ms"
        ms < 60_000 -> "${ms / 1_000}s"
        ms < 3_600_000 -> "${ms / 60_000}m ${(ms % 60_000) / 1_000}s"
        ms < 86_400_000 -> "${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m"
        else -> "${ms / 86_400_000}d ${(ms % 86_400_000) / 3_600_000}h"
    }
}
