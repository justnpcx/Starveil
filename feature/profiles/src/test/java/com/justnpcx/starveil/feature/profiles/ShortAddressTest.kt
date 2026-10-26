package com.justnpcx.starveil.feature.profiles

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The suffix that tells two identically named nodes apart.
 *
 * It has to be short enough to sit beside a name that is already ellipsised,
 * and specific enough that the two rows are actually different — which is the
 * whole point of adding it.
 */
class ShortAddressTest {

    @Test
    fun `a hostname keeps its first label`() {
        assertEquals("hk1:443", shortAddress("hk1.example.com", 443))
        assertEquals("jp2:2053", shortAddress("jp2.edge.example.net", 2053))
    }

    @Test
    fun `an address keeps its last two groups`() {
        assertEquals("50.218:8443", shortAddress("192.168.50.218", 8443))
        assertEquals("50.218:8444", shortAddress("192.168.50.218", 8444))
    }

    @Test
    fun `an IPv6 address stays readable`() {
        assertEquals("1:2:443", shortAddress("2001:db8::1:2", 443))
    }

    @Test
    fun `two nodes on one host differ by port`() {
        val a = shortAddress("node.example.com", 443)
        val b = shortAddress("node.example.com", 8443)
        assertEquals(false, a == b)
    }
}
