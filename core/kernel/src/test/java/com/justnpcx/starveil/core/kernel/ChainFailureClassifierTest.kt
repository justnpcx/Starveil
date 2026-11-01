package com.justnpcx.starveil.core.kernel

import org.junit.Assert.assertEquals
import org.junit.Test

class ChainFailureClassifierTest {
    @Test
    fun `a front outbound error identifies the first dial`() {
        val error = "outbound/vless[front]: dial tcp 203.0.113.1:443: connect: connection refused"
        assertEquals(ChainFailureStage.FRONT, ChainFailureClassifier.classify(error))
    }

    @Test
    fun `an exit outbound error identifies the dial through the front`() {
        val error = "outbound/shadowsocks[proxy-2]: dial tcp 198.51.100.2:8388: i/o timeout"
        assertEquals(ChainFailureStage.EXIT, ChainFailureClassifier.classify(error))
    }

    @Test
    fun `ambiguous evidence stays unsegmented`() {
        assertEquals(ChainFailureStage.UNSEGMENTED, ChainFailureClassifier.classify("dns: exchange failed"))
        assertEquals(
            ChainFailureStage.UNSEGMENTED,
            ChainFailureClassifier.classify("outbound/vless[front] and outbound/vless[proxy] failed"),
        )
    }
}
