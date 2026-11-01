package com.justnpcx.starveil.core.model

/**
 * One row of the live connection table.
 *
 * The kernel reports a connection three times — when it is decided, as it
 * carries traffic, and when it ends — so this is the folded result: one row per
 * connection, with the counters of the last report and the time it closed.
 *
 * No payload is kept, and none is available: the kernel hands over metadata
 * (who, where, which rule, which outbound, how many bytes) and never the
 * request. That is the whole reason this table can be shown at all.
 */
data class ConnectionDetail(
    val id: String,
    /** Package the kernel attributed it to; empty when it could not. */
    val appPackage: String,
    /** Host or address the app asked for. */
    val destination: String,
    /** Sniffed domain; empty for a bare-IP connection. */
    val domain: String,
    /** The rule that decided it, in the kernel's own words. */
    val rule: String,
    /** The outbound's tag. */
    val outbound: String,
    /** The outbound's type: `direct`, `block`, or a protocol. */
    val outboundType: String,
    val startedAtMs: Long,
    /** Epoch millis it ended; 0 while it is still open. */
    val closedAtMs: Long,
    val uplinkBytes: Long,
    val downlinkBytes: Long,
    val network: String,
    val protocol: String,
) {
    /** What to show as the request: the domain if sniffed, else the address. */
    val label: String get() = domain.ifBlank { destination }

    val open: Boolean get() = closedAtMs == 0L

    /** Bytes in both directions; what the row's "bytes" column shows. */
    val totalBytes: Long get() = uplinkBytes + downlinkBytes

    /**
     * How long it ran, or has been running, as of [nowMs].
     *
     * Null when the start is not a usable instant: 0, negative, or later than
     * [nowMs]. The kernel reports a start of 0 for a connection it only saw the
     * end of, and subtracting that from the current time produced a duration of
     * about thirty-nine years — a number that reads as a bug in the app rather
     * than as a gap in the data.
     */
    fun durationMs(nowMs: Long): Long? =
        if (startedAtMs <= 0L || startedAtMs > nowMs) {
            null
        } else {
            ((if (closedAtMs > 0L) closedAtMs else nowMs) - startedAtMs).coerceAtLeast(0L)
        }
}
