package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SIP008 — the Shadowsocks subscription format.
 *
 * The interesting cases are the malformed ones: a server entry that is
 * missing a field must be dropped, not imported half-built, because a
 * profile without a method fails at connect with a message about the kernel.
 */
class Sip008SubscriptionParserTest {

    private val parser = Sip008SubscriptionParser()

    @Test
    fun `a well-formed subscription becomes Shadowsocks profiles`() {
        val json = """
            {"version":1,"servers":[
              {"id":"a","remarks":"Tokyo","server":"1.2.3.4","server_port":8388,
               "password":"secret","method":"aes-256-gcm"},
              {"id":"b","server":"5.6.7.8","server_port":443,
               "password":"pw","method":"chacha20-ietf-poly1305"}
            ]}
        """.trimIndent()

        val profiles = parser.parse(json)

        assertEquals(2, profiles.size)
        assertEquals("Tokyo", profiles[0].name)
        assertEquals(ProfileType.SHADOWSOCKS, profiles[0].type)
        assertEquals("1.2.3.4", profiles[0].server)
        assertEquals(8388, profiles[0].port)
        assertEquals(
            ShadowsocksSettings(method = "aes-256-gcm", password = "secret"),
            profiles[0].settings,
        )
        // No remarks: the address is a better name than an empty one.
        assertEquals("5.6.7.8:443", profiles[1].name)
    }

    @Test
    fun `an entry missing a required field is dropped, the rest survive`() {
        val json = """
            {"version":1,"servers":[
              {"server":"1.2.3.4","server_port":8388,"password":"p","method":"aes-256-gcm"},
              {"remarks":"no method","server":"1.2.3.5","server_port":8388,"password":"p"},
              {"remarks":"no port","server":"1.2.3.6","password":"p","method":"aes-256-gcm"},
              {"remarks":"bad port","server":"1.2.3.7","server_port":70000,"password":"p","method":"aes-256-gcm"}
            ]}
        """.trimIndent()

        assertEquals(1, parser.parse(json).size)
        // Three of four entries were recognised and refused — the count the
        // UI now reports instead of silently importing one of four.
        assertEquals(3, parser.parseDetailed(json).dropped)
    }

    @Test
    fun `garbage is an empty list, not a crash`() {
        assertTrue(parser.parse("not json").isEmpty())
        assertTrue(parser.parse("{}").isEmpty())
        assertTrue(parser.parse("""{"servers":"nope"}""").isEmpty())
    }

    @Test
    fun `sniff recognises SIP008 and nothing else`() {
        assertTrue(parser.let { Sip008SubscriptionParser.sniff("""{"servers":[]}""") })
        assertFalse(Sip008SubscriptionParser.sniff("""{"outbounds":[]}"""))
        assertFalse(Sip008SubscriptionParser.sniff("proxies:\n  - name: x"))
        assertFalse(Sip008SubscriptionParser.sniff("dmxlc3M6Ly8="))
    }
}
