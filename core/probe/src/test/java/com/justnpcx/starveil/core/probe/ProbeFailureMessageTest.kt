package com.justnpcx.starveil.core.probe

import org.junit.Assert.assertEquals
import org.junit.Test

class ProbeFailureMessageTest {
    @Test
    fun `resolution and connection failures say what actually failed`() {
        assertEquals(R.string.latency_resolution_failed, manualFailureMessage(ProbeResult.ResolutionFailed))
        assertEquals(R.string.latency_unreachable, manualFailureMessage(ProbeResult.ConnectionFailed))
    }
}
