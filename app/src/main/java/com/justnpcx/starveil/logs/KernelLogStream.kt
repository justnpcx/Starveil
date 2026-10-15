package com.justnpcx.starveil.logs

import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelLogLine
import com.justnpcx.starveil.core.kernel.LogLevel
import com.justnpcx.starveil.core.kernel.LogStream
import com.justnpcx.starveil.core.model.AppLogLevel
import com.justnpcx.starveil.core.model.AppLogLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Adapts the kernel's log flow to the UI's [LogStream].
 *
 * Lives in :app because it is the only layer allowed to touch the kernel. The
 * lines are already redacted by the bridge; this only maps types and keeps a
 * bounded ring, so a long session cannot grow the list without limit.
 */
class KernelLogStream(bridge: KernelBridge) : LogStream {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ring = MutableStateFlow<List<AppLogLine>>(emptyList())

    /**
     * Hands each line a unique id. Two lines can carry the same timestamp and
     * the same text (sing-box logs a connection more than once within a
     * millisecond), and the viewer keys its list on this — a duplicate key
     * throws. Assigned here, outside the `update` lambda, so a CAS retry cannot
     * skip numbers.
     */
    private var nextSeq = 0L

    override val lines: Flow<List<AppLogLine>> = ring.asStateFlow()

    init {
        scope.launch {
            bridge.logs.collect { line ->
                // sing-box's DEBUG output is DNS chatter — dozens of lines a
                // second — and a bounded ring cannot hold it plus the lines we
                // actually diagnose with: it evicts the protect/interface/DNS
                // diagnostics within seconds. Keep INFO and above from sing-box,
                // and keep our own DEBUG (tag "kernel") in full.
                if (line.level == LogLevel.DEBUG && line.tag != DIAGNOSTIC_TAG) return@collect
                val seq = nextSeq++
                ring.update { (it + line.toAppLogLine(seq)).takeLast(RING_SIZE) }
            }
        }
    }

    private fun KernelLogLine.toAppLogLine(seq: Long) = AppLogLine(
        level = when (level) {
            LogLevel.DEBUG -> AppLogLevel.DEBUG
            LogLevel.INFO -> AppLogLevel.INFO
            LogLevel.WARN -> AppLogLevel.WARN
            LogLevel.ERROR -> AppLogLevel.ERROR
        },
        tag = tag,
        message = message,
        timestampMs = timestampMs,
        seq = seq,
    )

    private companion object {
        /** Ring size: enough to diagnose a session, small enough to stay cheap. */
        const val RING_SIZE = 500

        /** Tag on lines this app writes itself, as opposed to sing-box's own. */
        const val DIAGNOSTIC_TAG = "kernel"
    }
}
