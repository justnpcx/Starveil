package com.justnpcx.starveil.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The door an external link comes through.
 *
 * Two things matter here and both are security, not convenience: only the
 * schemes this app can parse get in, and the link is cleared when it is read so
 * an Activity recreated by a rotation does not import the same node twice.
 */
class DeepLinkInboxTest {

    private val inbox = DeepLinkInbox()

    @Test
    fun `a share link is accepted and waits`() {
        assertTrue(inbox.offer("ss://abc@example.com:8388#node"))

        assertTrue(inbox.hasPending)
        assertEquals("ss://abc@example.com:8388#node", inbox.pending.value)
    }

    @Test
    fun `reading it clears it, so a rotation cannot import twice`() {
        inbox.offer("vless://uuid@example.com:443#node")

        assertEquals("vless://uuid@example.com:443#node", inbox.consume())
        assertNull(inbox.consume())
        assertFalse(inbox.hasPending)
    }

    @Test
    fun `a scheme this app cannot parse is refused`() {
        // Untrusted input: refused at the door rather than handed to a parser
        // that would report it later as an unreadable link.
        assertFalse(inbox.offer("clash://install-config?url=https://example.com"))
        assertFalse(inbox.hasPending)
    }

    @Test
    fun `http and https are refused`() {
        // A subscription URL is one of these, and registering them would put
        // the app in the chooser for every link tapped in a browser.
        assertFalse(inbox.offer("https://example.com/sub"))
        assertFalse(inbox.offer("http://example.com/sub"))
        assertFalse(inbox.hasPending)
    }

    @Test
    fun `nothing at all is refused`() {
        assertFalse(inbox.offer(null))
        assertFalse(inbox.offer(""))
        assertFalse(inbox.offer("   "))
        assertFalse(inbox.offer("no-scheme-here"))
        assertFalse(inbox.hasPending)
    }

    @Test
    fun `every scheme the parser handles is accepted`() {
        for (scheme in DeepLinkInbox.SUPPORTED_SCHEMES) {
            val inbox = DeepLinkInbox()
            assertTrue("$scheme should be accepted", inbox.offer("$scheme://x@example.com:1#n"))
        }
    }

    @Test
    fun `the scheme is matched regardless of case`() {
        // Browsers and QR scanners do not agree on this, and a link that works
        // only in lower case is a bug the user cannot diagnose.
        assertTrue(inbox.offer("SS://abc@example.com:8388#node"))
        assertTrue(inbox.hasPending)
    }

    @Test
    fun `a second link replaces the first`() {
        // Either the same intent delivered twice or a newer tap; the newest is
        // the one the user meant.
        inbox.offer("ss://first@example.com:1#one")
        inbox.offer("ss://second@example.com:2#two")

        assertEquals("ss://second@example.com:2#two", inbox.consume())
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        // A link copied out of a chat message often carries a newline.
        assertTrue(inbox.offer("  ss://abc@example.com:8388#node\n"))
        assertEquals("ss://abc@example.com:8388#node", inbox.consume())
    }

    @Test
    fun `an unsupported link does not disturb one already waiting`() {
        inbox.offer("ss://good@example.com:1#good")

        assertFalse(inbox.offer("clash://nope"))

        assertEquals("ss://good@example.com:1#good", inbox.consume())
    }
}