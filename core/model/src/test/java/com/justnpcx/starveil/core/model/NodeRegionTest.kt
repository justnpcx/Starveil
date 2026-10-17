package com.justnpcx.starveil.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The region classifier.
 *
 * The table is data, but the matching rules are not: substring for words,
 * whole-token for two-letter codes, earliest match wins. Those three rules are
 * what keep `Russia` out of the US group and make `香港→日本 中转` predictable,
 * so they are what this pins.
 */
class NodeRegionTest {

    private fun region(name: String) = NodeRegions.classify(name)?.region

    @Test
    fun `Chinese names and flags are matched`() {
        assertEquals(NodeRegion.HONG_KONG, region("🇭🇰 香港 01"))
        assertEquals(NodeRegion.JAPAN, region("日本 东京 IEPL"))
        assertEquals(NodeRegion.SINGAPORE, region("新加坡 03"))
        assertEquals(NodeRegion.UNITED_STATES, region("美国 洛杉矶 02"))
    }

    @Test
    fun `English names and cities are matched`() {
        assertEquals(NodeRegion.HONG_KONG, region("Hong Kong 01"))
        assertEquals(NodeRegion.JAPAN, region("Tokyo Premium"))
        assertEquals(NodeRegion.UNITED_STATES, region("Los Angeles 03"))
    }

    @Test
    fun `a two-letter code counts only as its own token`() {
        assertEquals(NodeRegion.UNITED_STATES, region("US-LA-01"))
        assertEquals(NodeRegion.UNITED_STATES, region("US01"))
        assertEquals(NodeRegion.HONG_KONG, region("hk-02"))
        // The trap: "us" is inside "Russia" and "jp" is inside "Japan", but
        // neither is a code there.
        assertEquals(NodeRegion.RUSSIA, region("Russia Moscow"))
        assertEquals(NodeRegion.JAPAN, region("Japan Osaka"))
    }

    @Test
    fun `a code followed by a digit still counts`() {
        // Providers write "us01", "hk02" far more often than "us-01".
        assertEquals(NodeRegion.UNITED_STATES, region("us01"))
        assertEquals(NodeRegion.HONG_KONG, region("HK02 IEPL"))
    }

    @Test
    fun `the earliest match wins`() {
        // A relay is named by where it enters, which is the leading word.
        val match = NodeRegions.classify("香港→日本 中转")
        assertEquals(NodeRegion.HONG_KONG, match?.region)
        assertEquals("香港", match?.token)
    }

    @Test
    fun `a city after its country does not change the answer`() {
        assertEquals(NodeRegion.UNITED_STATES, region("美国 洛杉矶"))
        assertEquals(NodeRegion.JAPAN, region("日本 大阪"))
    }

    @Test
    fun `an unrecognised name has no region`() {
        assertNull(region("Node 01"))
        assertNull(region(""))
        assertNull(region("   "))
        assertNull(region("Premium Relay A"))
    }

    @Test
    fun `the same name always lands in the same region`() {
        val name = "🇸🇬 Singapore 05 IPLC"
        val first = NodeRegions.classify(name)
        repeat(5) { assertEquals(first, NodeRegions.classify(name)) }
    }
}
