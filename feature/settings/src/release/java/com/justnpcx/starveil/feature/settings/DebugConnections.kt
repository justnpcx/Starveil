package com.justnpcx.starveil.feature.settings

import com.justnpcx.starveil.core.model.ConnectionDetail

/** Nothing to add in a release build. See src/debug for the other half. */
internal object DebugConnections {
    fun extraRows(nowMs: Long): List<ConnectionDetail> = emptyList()
}
