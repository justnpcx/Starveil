package com.justnpcx.starveil.core.data.log

import com.justnpcx.starveil.core.model.AppLogLevel
import com.justnpcx.starveil.core.model.AppLogLine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Log lines this app writes itself, for the Logs screen and the diagnostics
 * export.
 *
 * The viewer already has one channel — the kernel's ring — and a second viewer
 * for "our" lines would be a second place to look. This sink feeds that same
 * ring: :app's log stream merges it, so a failure here shows up beside the
 * kernel's own lines with a tag that says where it came from.
 *
 * A shared flow rather than a state: a log line is an event. The ring's
 * bounded history is the reader's business, not this sink's.
 */
@Singleton
class AppLogSink @Inject constructor() {

    private val _lines = MutableSharedFlow<AppLogLine>(extraBufferCapacity = 32)

    val lines: SharedFlow<AppLogLine> = _lines.asSharedFlow()

    fun warn(tag: String, message: String) {
        emit(AppLogLevel.WARN, tag, message)
    }

    fun info(tag: String, message: String) {
        emit(AppLogLevel.INFO, tag, message)
    }

    private fun emit(level: AppLogLevel, tag: String, message: String) {
        // The id is assigned where the line enters the ring; a line that never
        // reaches one is dropped rather than given a made-up id.
        _lines.tryEmit(
            AppLogLine(
                level = level,
                tag = tag,
                message = message,
                timestampMs = System.currentTimeMillis(),
                seq = 0L,
            ),
        )
    }
}
