package com.justnpcx.starveil.core.kernel.libbox

import com.justnpcx.starveil.core.kernel.LogRedactor

import com.justnpcx.starveil.core.kernel.ConnectionEventKind
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
     * Routing decisions, for the flow view and the connection detail.
     *
     * All three events are reported: the flow view only needs the first one
     * (the decision was made once, at the start), but the connection detail
     * needs the updates and the close to show a live byte count and a row that
     * stops saying "connected" the moment it ends. Filtering is the consumer's
     * job — see [com.justnpcx.starveil.core.probe.KernelFlowLog], which keeps
     * only NEW.
     *
     * The rule text is redacted because it is free-form; the destination and
     * domain are not, because they are the thing being diagnosed and a hostname
     * is not a credential.
     */
    override fun writeConnectionEvents(events: ConnectionEvents) {
        val iterator = events.iterator()
        while (iterator.hasNext()) {
            val event = iterator.next()
            val connection = event.connection
            // An update or a close carries only the id, the byte deltas and
            // (for a close) the time — the kernel does not repeat the
            // connection. A new event always carries one; without it there is
            // nothing to report.
            if (connection == null) {
                if (event.type == EVENT_NEW) continue
                onConnection(
                    KernelConnection(
                        id = event.id.orEmpty(),
                        destination = "",
                        domain = "",
                        rule = "",
                        outbound = "",
                        outboundType = "",
                        createdAtMs = 0L,
                        event = if (event.type == EVENT_CLOSE) {
                            ConnectionEventKind.CLOSE
                        } else {
                            ConnectionEventKind.UPDATE
                        },
                        uplinkDelta = event.uplinkDelta,
                        downlinkDelta = event.downlinkDelta,
                        closedAtMs = event.closedAt,
                    ),
                )
                continue
            }
            onConnection(
                KernelConnection(
                    id = connection.id.orEmpty(),
                    destination = connection.destination.orEmpty(),
                    domain = connection.domain.orEmpty(),
                    rule = LogRedactor.redact(connection.rule.orEmpty()),
                    outbound = connection.outbound.orEmpty(),
                    outboundType = connection.outboundType.orEmpty(),
                    createdAtMs = connection.createdAt,
                    event = when (event.type) {
                        EVENT_NEW -> ConnectionEventKind.NEW
                        EVENT_UPDATE -> ConnectionEventKind.UPDATE
                        EVENT_CLOSE -> ConnectionEventKind.CLOSE
                        // A type this build does not know is treated as an
                        // update: it cannot be a new decision, and dropping it
                        // would freeze a row that is still moving.
                        else -> ConnectionEventKind.UPDATE
                    },
                    appPackage = connection.processInfo?.packageName().orEmpty(),
                    uplinkBytes = connection.uplinkTotal,
                    downlinkBytes = connection.downlinkTotal,
                    uplinkDelta = event.uplinkDelta,
                    downlinkDelta = event.downlinkDelta,
                    closedAtMs = connection.closedAt.takeIf { it > 0L } ?: event.closedAt,
                    network = connection.network.orEmpty(),
                    protocol = connection.protocol.orEmpty(),
                ),
            )
        }
    }

    /**
     * The first package name the kernel attributed, or empty.
     *
     * An app can own more than one package; the first is the one the kernel
     * matched, and a detail row has space for one.
     */
    private fun io.nekohasekai.libbox.ProcessInfo.packageName(): String {
        val names = packageNames()
        return if (names.hasNext()) names.next().orEmpty() else ""
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
         * `libbox.ConnectionEventNew/Update/Close` — gobind does not export the
         * Go constants, so these are the values from `command_types.go`
         * (`ConnectionEventNew = iota`, then Update, then Close).
         */
        const val EVENT_NEW = 0
        const val EVENT_UPDATE = 1
        const val EVENT_CLOSE = 2

        /**
         * A connection the app abandoned before sing-box finished establishing
         * it. Deliberately narrow — only this one sing-box message is dropped,
         * so anything else still reaches the log. See [writeLogs].
         */
        val BENIGN_HANDSHAKE_REPORT = Regex("report handshake success: .*connection refused")
    }
}
