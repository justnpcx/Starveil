package com.justnpcx.starveil.core.kernel.libbox

import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.kernel.KernelLogLine
import com.justnpcx.starveil.core.kernel.LogLevel
import com.justnpcx.starveil.core.kernel.TrafficStats
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.OutboundGroupItemIterator
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges the kernel's pushed callbacks into the `KernelBridge` contract.
 *
 * Traffic stats arrive here (sing-box pushes `StatusMessage`; there is no
 * getter), so [stats] is a hot cache that `queryStats()` can read without
 * blocking — the "pushed, never polled" model the contract asks for.
 *
 * Logs are **redacted before they leave this class**: the kernel is supposed
 * to avoid printing credentials, but the contract puts the obligation on the
 * bridge, so we do not trust it (README 安全红线).
 */
class StarveilCommandClientHandler(
    private val onConnected: () -> Unit = {},
    private val onDisconnected: (String) -> Unit = {},
    private val onLog: (KernelLogLine) -> Unit = {},
    private val onConnection: (KernelConnection) -> Unit = {},
) : CommandClientHandler {

    private val _stats = MutableStateFlow(TrafficStats())
    val stats: StateFlow<TrafficStats> = _stats.asStateFlow()

    override fun writeStatus(status: StatusMessage) {
        _stats.value = TrafficStats(
            uplinkBytes = status.uplinkTotal,
            downlinkBytes = status.downlinkTotal,
            uplinkRate = status.uplink,
            downlinkRate = status.downlink,
        )
        // Diagnostic: whether the status stream is subscribed at all. Counters
        // carry no credentials.
        android.util.Log.i(
            "StarveilKernel",
            "writeStatus: up=${status.uplink}/${status.uplinkTotal} down=${status.downlink}/${status.downlinkTotal}",
        )
    }

    /**
     * Not subscribed: the outbound stream carried nothing but url-test
     * results, which the app no longer asks the kernel for (node latency is
     * probed directly — see NodeLatencyTester).
     */
    override fun writeOutbounds(outbounds: OutboundGroupItemIterator) {
        while (outbounds.hasNext()) outbounds.next()
    }

    override fun writeLogs(logs: LogIterator) {
        while (logs.hasNext()) {
            val entry = logs.next()
            val redacted = LogRedactor.redact(entry.message)
            // Benign, and loud enough to bury real errors. sing-tun's gvisor
            // stack is lazy: it only answers the app's SYN once sing-box has
            // established the outbound, which takes a few hundred ms on a
            // distant node. An app that gives up in that window — a browser
            // preconnect, a speculative socket — leaves the inbound gone, so
            // the deferred handshake fails and sing-box logs it as an error.
            // Nothing is actionable: the connection was already abandoned, and
            // a deliberate connection produces none of these.
            if (BENIGN_HANDSHAKE_REPORT.containsMatchIn(redacted)) continue
            // Diagnostic: sing-box's own view of what is going wrong. The level
            // numbers are inverted from severity — the device shows INFO as 4
            // and DEBUG as 5, so errors are the LOW numbers and ">= 3" would
            // have filtered out every error line. Keep warn and worse.
            if (entry.level <= LEVEL_WARN) {
                android.util.Log.i("StarveilKernel", "sing-box[${entry.level}]: $redacted")
            }
            onLog(
                KernelLogLine(
                    level = mapLevel(entry.level),
                    tag = "sing-box",
                    message = redacted,
                ),
            )
        }
    }

    override fun connected() {
        android.util.Log.i("StarveilKernel", "command client connected")
        onLog(KernelLogLine(level = LogLevel.INFO, tag = "kernel", message = "command client connected"))
        onConnected()
    }

    override fun disconnected(message: String) {
        android.util.Log.w("StarveilKernel", "command client disconnected: $message")
        onLog(
            KernelLogLine(
                level = LogLevel.WARN,
                tag = "kernel",
                message = "command client disconnected: ${LogRedactor.redact(message)}",
            ),
        )
        onDisconnected(message)
    }

    override fun clearLogs() {}

    override fun initializeClashMode(modes: StringIterator, current: String) {}

    override fun updateClashMode(mode: String) {}

    override fun setDefaultLogLevel(level: Int) {}

    override fun writeGroups(groups: OutboundGroupIterator) {}

    /**
     * Routing decisions, for the flow view.
     *
     * Only [EVENT_NEW] is reported. Update and close events repeat a connection
     * already reported — they fire continuously as it carries traffic — and the
     * decision being shown was made once, at the start. Dropping them here also
     * keeps the volume down to one event per connection rather than a stream.
     *
     * The rule text is redacted because it is free-form; the destination and
     * domain are not, because they are the thing being diagnosed and a hostname
     * is not a credential.
     */
    override fun writeConnectionEvents(events: ConnectionEvents) {
        val iterator = events.iterator()
        while (iterator.hasNext()) {
            val event = iterator.next()
            if (event.type != EVENT_NEW) continue
            val connection = event.connection ?: continue
            onConnection(
                KernelConnection(
                    id = connection.id.orEmpty(),
                    destination = connection.destination.orEmpty(),
                    domain = connection.domain.orEmpty(),
                    rule = LogRedactor.redact(connection.rule.orEmpty()),
                    outbound = connection.outbound.orEmpty(),
                    outboundType = connection.outboundType.orEmpty(),
                    createdAtMs = connection.createdAt,
                ),
            )
        }
    }

    /**
     * sing-box's levels count down from panic(0) to trace(6), so the ordering
     * is the opposite of severity: 4 is INFO and 5 is DEBUG on device.
     */
    private fun mapLevel(level: Int): LogLevel = when (level) {
        0, 1, 2 -> LogLevel.ERROR
        3 -> LogLevel.WARN
        4 -> LogLevel.INFO
        else -> LogLevel.DEBUG
    }

    private companion object {
        const val LEVEL_WARN = 3

        /**
         * `libbox.ConnectionEventNew` — gobind does not export the Go
         * constants, so this is the value from `command_types.go`
         * (`ConnectionEventNew = iota`).
         */
        const val EVENT_NEW = 0

        /**
         * A connection the app abandoned before sing-box finished establishing
         * it. Deliberately narrow — only this one sing-box message is dropped,
         * so anything else still reaches the log. See [writeLogs].
         */
        val BENIGN_HANDSHAKE_REPORT = Regex("report handshake success: .*connection refused")
    }
}

/**
 * Strips anything that looks like a credential from a kernel log line.
 *
 * Deliberately conservative: it would rather mangle a benign line than leak a
 * UUID, a password, or a subscription URL's query string.
 */
object LogRedactor {

    private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val URL_QUERY = Regex("(https?://[^\\s?]+)\\?[^\\s]*")
    private val SECRET_KV = Regex("(?i)(password|passwd|uuid|secret|token|private[_-]?key)\\s*[=:]\\s*\\S+")
    /** sing-box colours its log lines; the viewer shows the raw escape codes. */
    private val ANSI = Regex("\u001B\\[[0-9;]*[A-Za-z]")

    fun redact(message: String): String {
        var out = message
        out = ANSI.replace(out, "")
        out = UUID.replace(out, "<uuid>")
        out = URL_QUERY.replace(out, "$1?<redacted>")
        out = SECRET_KV.replace(out, "$1=<redacted>")
        return out
    }
}
