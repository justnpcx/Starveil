package com.justnpcx.starveil.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The kernel reports an outbound's *type*, and the flow view shows three
 * outcomes. Getting this wrong is not a cosmetic bug: it would tell someone
 * their traffic was blocked when it was proxied, or vice versa.
 */
class FlowRecordTest {

    @Test
    fun `the kernel's outbound type maps onto the three outcomes`() {
        assertEquals(FlowOutcome.DIRECT, FlowOutcome.fromOutboundType("direct"))
        assertEquals(FlowOutcome.BLOCK, FlowOutcome.fromOutboundType("block"))
        // Everything else the kernel reports here is the protocol that carried
        // it, which is a proxy by definition.
        assertEquals(FlowOutcome.PROXY, FlowOutcome.fromOutboundType("vless"))
        assertEquals(FlowOutcome.PROXY, FlowOutcome.fromOutboundType("hysteria2"))
        assertEquals(FlowOutcome.PROXY, FlowOutcome.fromOutboundType("selector"))
    }

    @Test
    fun `an unrecognised outbound type reads as proxied, not as blocked`() {
        // The kernel sends everything it does not reject or route direct
        // through an outbound, so "proxied" is the safe reading of an unknown
        // type. Calling it blocked would be the worse lie: it claims traffic
        // was stopped when it was not.
        assertEquals(FlowOutcome.PROXY, FlowOutcome.fromOutboundType(""))
        assertEquals(FlowOutcome.PROXY, FlowOutcome.fromOutboundType("some-future-protocol"))
    }

    @Test
    fun `the label prefers the sniffed domain and falls back to the address`() {
        // A bare-IP connection has no domain — the kernel never sniffed one —
        // and showing an empty row would say nothing about what it was.
        assertEquals("example.com", record(domain = "example.com").label)
        assertEquals("1.2.3.4:443", record(domain = "").label)
    }

    private fun record(domain: String) = FlowRecord(
        connectionId = "c",
        destination = "1.2.3.4:443",
        domain = domain,
        rule = "domain_suffix=[example.com]",
        outcome = FlowOutcome.PROXY,
        createdAtMs = 0L,
    )
}
