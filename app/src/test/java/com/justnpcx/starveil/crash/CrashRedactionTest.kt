package com.justnpcx.starveil.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide what a crash report may contain.
 *
 * Each case is a string that has actually appeared in this project's logs or
 * exceptions, or that the kernel produces when a node fails.
 */
class CrashRedactionTest {

    @Test
    fun `a share link is removed whole`() {
        val text = "failed to parse vless://0f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b@node.example.com:443?type=xhttp#香港01"
        val scrubbed = CrashRedaction.scrub(text)

        assertEquals("failed to parse [redacted link]", scrubbed)
        assertFalse(scrubbed.contains("node.example.com"))
        assertFalse(scrubbed.contains("0f1e2d3c"))
    }

    @Test
    fun `a subscription url is removed`() {
        val text = "GET https://sub.example.com/api/v1/client/subscribe?token=deadbeef failed"
        assertEquals("GET [redacted url] failed", CrashRedaction.scrub(text))
    }

    @Test
    fun `a bare uuid is removed`() {
        val text = "invalid uuid 0f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b in profile"
        assertEquals("invalid uuid [redacted uuid] in profile", CrashRedaction.scrub(text))
    }

    @Test
    fun `an address and port are removed but the reason stays`() {
        // This is the exact shape of the kernel's dial error.
        val text = "dial tcp 203.0.113.9:443: connect: connection refused"
        assertEquals(
            "dial tcp [redacted address]: connect: connection refused",
            CrashRedaction.scrub(text),
        )
    }

    @Test
    fun `an ipv6 address is removed`() {
        val text = "dial tcp [2001:db8:85a3::8a2e:370:7334]:443: no route to host"
        assertFalse(CrashRedaction.scrub(text).contains("2001:db8"))
    }

    @Test
    fun `a long key is removed`() {
        val text = "bad private key " + "A".repeat(43) + "="
        assertEquals("bad private key [redacted secret]", CrashRedaction.scrub(text))
    }

    @Test
    fun `ordinary text is untouched`() {
        val text = "java.lang.IllegalStateException: tunnel stopped while connecting"
        assertEquals(text, CrashRedaction.scrub(text))
    }

    @Test
    fun `a clock time is not mistaken for an address`() {
        val text = "timeout after 12:34:56"
        assertEquals(text, CrashRedaction.scrub(text))
    }

    @Test
    fun `every occurrence is replaced`() {
        val text = "1.2.3.4 then 5.6.7.8"
        assertEquals("[redacted address] then [redacted address]", CrashRedaction.scrub(text))
    }

    @Test
    fun `a message with several kinds of secret is cleaned in one pass`() {
        val text = "connect 198.51.100.7:8443 with 0f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b via https://x.example.com/p?t=1"
        val scrubbed = CrashRedaction.scrub(text)

        assertTrue(scrubbed.contains("[redacted address]"))
        assertTrue(scrubbed.contains("[redacted uuid]"))
        assertTrue(scrubbed.contains("[redacted url]"))
        assertFalse(scrubbed.contains("198.51.100.7"))
        assertFalse(scrubbed.contains("0f1e2d3c"))
    }
}
