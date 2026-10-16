package com.justnpcx.starveil.core.kernel

import com.justnpcx.starveil.core.model.FlowRecord
import kotlinx.coroutines.flow.Flow

/**
 * The kernel's routing decisions, for the flow view.
 *
 * A narrow port like [LogStream], and for the same reason: a feature module
 * sees a bounded list of [FlowRecord] and nothing kernel-shaped. :app
 * implements it over [KernelBridge.connections].
 *
 * The list is bounded by the implementation (a ring, newest first), because
 * the contract for the viewer is a ring buffer, never an unbounded List.
 */
interface FlowLog {
    val records: Flow<List<FlowRecord>>
}
