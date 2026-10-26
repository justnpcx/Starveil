package com.justnpcx.starveil.core.kernel

import com.justnpcx.starveil.core.model.ConnectionDetail
import kotlinx.coroutines.flow.Flow

/**
 * The kernel's live connection table, for the diagnostics screen.
 *
 * A narrow port like [FlowLog], and for the same reason: a feature module sees
 * a bounded list of [ConnectionDetail] and nothing kernel-shaped. :app
 * implements it over [KernelBridge.connections].
 *
 * Unlike [FlowLog] this keeps updates and closes, because the question it
 * answers is "what is happening now", and a row that never notices its
 * connection ended is worse than no row at all. The list is bounded by the
 * implementation (a ring, newest first).
 */
interface ConnectionLog {
    val connections: Flow<List<ConnectionDetail>>
}
