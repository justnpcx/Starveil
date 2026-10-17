package com.justnpcx.starveil.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Name first, address second.
 *
 * The order is the whole point: a provider that writes 香港 on a node behind a
 * US CDN is describing the exit, and GeoIP only ever describes the host.
 */
class NodeRegionResolveTest {

    @Test
    fun `the name wins when it says something`() {
        assertEquals(NodeRegion.HONG_KONG, NodeRegions.resolve("香港-01", country = "US"))
    }

    @Test
    fun `the address is used when the name says nothing`() {
        assertEquals(NodeRegion.UNITED_STATES, NodeRegions.resolve("HY2+Reality-hy2-443", "US"))
        assertEquals(NodeRegion.JAPAN, NodeRegions.resolve("node-07", "jp"))
    }

    @Test
    fun `neither means no region`() {
        assertNull(NodeRegions.resolve("cf-xhttp", null))
        assertNull(NodeRegions.resolve("cf-xhttp", "ZZ"))
    }

    @Test
    fun `every region knows its own country code`() {
        NodeRegion.entries.forEach { region ->
            assertEquals(region, NodeRegions.fromCountry(region.iso))
        }
    }

    @Test
    fun `flags come from the code, not a table`() {
        assertEquals("🇺🇸", NodeRegion.UNITED_STATES.flag)
        assertEquals("🇭🇰", NodeRegion.HONG_KONG.flag)
        assertEquals("🇬🇧", NodeRegion.UNITED_KINGDOM.flag)
    }
}
