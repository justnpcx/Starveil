package com.justnpcx.starveil.core.kernel.libbox

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
    }

    override fun writeLogs(logs: LogIterator) {
        while (logs.hasNext()) {
            val entry = logs.next()
            onLog(
                KernelLogLine(
                    level = mapLevel(entry.level),
                    tag = "sing-box",
                    message = LogRedactor.redact(entry.message),
                ),
            )
        }
    }

    override fun connected() = onConnected()

    override fun disconnected(message: String) = onDisconnected(message)

    override fun clearLogs() {}

    override fun initializeClashMode(modes: StringIterator, current: String) {}

    override fun updateClashMode(mode: String) {}

    override fun setDefaultLogLevel(level: Int) {}

    override fun writeGroups(groups: OutboundGroupIterator) {}

    override fun writeOutbounds(outbounds: OutboundGroupItemIterator) {}

    override fun writeConnectionEvents(events: ConnectionEvents) {}

    private fun mapLevel(level: Int): LogLevel = when (level) {
        0, 1 -> LogLevel.DEBUG
        2 -> LogLevel.INFO
        3 -> LogLevel.WARN
        else -> LogLevel.ERROR
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

    fun redact(message: String): String {
        var out = message
        out = UUID.replace(out, "<uuid>")
        out = URL_QUERY.replace(out, "$1?<redacted>")
        out = SECRET_KV.replace(out, "$1=<redacted>")
        return out
    }
}
