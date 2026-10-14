package com.justnpcx.starveil.core.kernel

import com.justnpcx.starveil.core.model.AppLogLine
import kotlinx.coroutines.flow.Flow

/**
 * The kernel's log lines, for the Logs screen.
 *
 * A narrow port on purpose: :feature:* may not import :core:kernel, and this
 * carries nothing kernel-specific — no sing-box type, no JNI, no process
 * handle. :app implements it over [KernelBridge.logs].
 *
 * The list is bounded by the implementation (a ring), because the contract for
 * the viewer is a ring buffer, never an unbounded List<LogLine>.
 */
interface LogStream {
    val lines: Flow<List<AppLogLine>>
}
