package com.justnpcx.starveil.core.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The body comes from someone else's server, so the parser does not assume. */
class ExitProbeTest {

    @Test
    fun `the address is the ip line, not the whole body`() {
        val body = """
            fl=123f45
            h=1.1.1.1
            ip=203.0.113.7
            ts=1700000000.123
            visit_scheme=https
        """.trimIndent()
        assertEquals("203.0.113.7", exitAddressFromTrace(body))
    }

    @Test
    fun `a body without an ip line is nothing, not an empty address`() {
        assertNull(exitAddressFromTrace("fl=123\nh=1.1.1.1"))
        assertNull(exitAddressFromTrace("ip=\n"))
        assertNull(exitAddressFromTrace(""))
    }

    @Test
    fun `an ipv6 address survives the trim`() {
        assertEquals("2001:db8::1", exitAddressFromTrace("ip=2001:db8::1\nts=1"))
    }
}
