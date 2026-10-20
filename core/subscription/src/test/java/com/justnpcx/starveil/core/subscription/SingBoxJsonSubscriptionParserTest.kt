package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.SecurityMode
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import com.justnpcx.starveil.core.model.TrojanSettings
import com.justnpcx.starveil.core.model.TransportType
import com.justnpcx.starveil.core.model.VlessSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A full sing-box config used as a subscription.
 *
 * The cases that matter are the ones that must be *dropped*: a REALITY
 * outbound without its key, an unknown protocol, a transport this app does
 * not model. Importing those with fields missing produces a node that cannot
 * connect and blames the kernel. REALITY *with* a key is imported in full —
 * it is the common shape of a modern subscription.
 */
class SingBoxJsonSubscriptionParserTest {

    private val parser = SingBoxJsonSubscriptionParser()

    @Test
    fun `the four supported protocols map field for field`() {
        val json = """
            {"outbounds":[
              {"type":"vless","tag":"vless-ws","server":"a.example.com","server_port":443,
               "uuid":"11111111-2222-3333-4444-555555555555","flow":"xtls-rprx-vision",
               "tls":{"enabled":true,"server_name":"a.example.com","alpn":["h2"],
                      "utls":{"enabled":true,"fingerprint":"chrome"}},
               "transport":{"type":"ws","path":"/x","headers":{"Host":"cdn.example.com"}}},
              {"type":"trojan","tag":"trojan","server":"b.example.com","server_port":8443,
               "password":"pw","tls":{"enabled":true}},
              {"type":"shadowsocks","tag":"ss","server":"c.example.com","server_port":8388,
               "method":"aes-256-gcm","password":"sspw"},
              {"type":"hysteria2","tag":"hy2","server":"d.example.com","server_port":443,
               "password":"hypw","tls":{"enabled":true,"server_name":"d.example.com","insecure":true}}
            ]}
        """.trimIndent()

        val profiles = parser.parse(json)

        assertEquals(4, profiles.size)

        val vless = profiles[0]
        assertEquals(ProfileType.VLESS, vless.type)
        assertEquals("vless-ws", vless.name)
        val vlessSettings = vless.settings as VlessSettings
        assertEquals("11111111-2222-3333-4444-555555555555", vlessSettings.uuid)
        assertEquals("xtls-rprx-vision", vlessSettings.flow)
        assertEquals(SecurityMode.TLS, vlessSettings.tls.security)
        assertEquals("chrome", vlessSettings.tls.fingerprint)
        assertEquals(listOf("h2"), vlessSettings.tls.alpn)
        assertEquals(TransportType.WS, vlessSettings.transport.type)
        assertEquals("/x", vlessSettings.transport.path)
        assertEquals("cdn.example.com", vlessSettings.transport.host)

        assertEquals(ProfileType.TROJAN, profiles[1].type)
        assertEquals(ProfileType.SHADOWSOCKS, profiles[2].type)
        assertEquals(
            ShadowsocksSettings(method = "aes-256-gcm", password = "sspw"),
            profiles[2].settings,
        )

        val hy2 = profiles[3]
        assertEquals(ProfileType.HYSTERIA2, hy2.type)
        val hy2Settings = hy2.settings as Hysteria2Settings
        assertEquals("hypw", hy2Settings.password)
        assertEquals(true, hy2Settings.tls.allowInsecure)
    }

    @Test
    fun `an outbound this app cannot build is dropped`() {
        val json = """
            {"outbounds":[
              {"type":"vless","tag":"reality","server":"a.example.com","server_port":443,
               "uuid":"u","tls":{"enabled":true,"reality":{"enabled":true}}},
              {"type":"wireguard","tag":"wg","server":"b.example.com","server_port":51820},
              {"type":"vless","tag":"no-uuid","server":"c.example.com","server_port":443},
              {"type":"trojan","tag":"ok","server":"d.example.com","server_port":443,
               "password":"pw","tls":{"enabled":true}}
            ]}
        """.trimIndent()

        val profiles = parser.parse(json)

        // Only the one that could actually connect.
        assertEquals(listOf("ok"), profiles.map { it.name })
        // The wireguard, the keyless REALITY and the uuid-less VLESS.
        assertEquals(3, parser.parseDetailed(json).dropped)
    }

    @Test
    fun `a reality outbound keeps its key, short id and fingerprint`() {
        val json = """
            {"outbounds":[
              {"type":"vless","tag":"r","server":"a.example.com","server_port":443,
               "uuid":"11111111-2222-3333-4444-555555555555",
               "tls":{"enabled":true,"server_name":"www.example.com","alpn":["h2"],
                      "utls":{"enabled":true,"fingerprint":"chrome"},
                      "reality":{"enabled":true,"public_key":"jNXHt1yRo0vDtaCyGjXeRzXk9pQ3mB8sWcVfL2uKn-_",
                                 "short_id":"ab12","support_x25519mlkem768":true}}},
              {"type":"trojan","tag":"rt","server":"b.example.com","server_port":443,
               "password":"pw",
               "tls":{"enabled":true,"server_name":"b.example.com",
                      "reality":{"enabled":true,"public_key":"key2"}}}
            ]}
        """.trimIndent()

        val outcome = parser.parseDetailed(json)

        assertEquals("nothing should be dropped", 0, outcome.dropped)
        val vless = outcome.profiles[0].settings as VlessSettings
        assertEquals(SecurityMode.REALITY, vless.tls.security)
        assertEquals("jNXHt1yRo0vDtaCyGjXeRzXk9pQ3mB8sWcVfL2uKn-_", vless.tls.reality?.publicKey)
        assertEquals("ab12", vless.tls.reality?.shortId)
        assertEquals(true, vless.tls.reality?.mlKem)
        assertEquals("www.example.com", vless.tls.sni)
        assertEquals("chrome", vless.tls.fingerprint)
        assertEquals(listOf("h2"), vless.tls.alpn)

        val trojan = outcome.profiles[1].settings as TrojanSettings
        assertEquals(SecurityMode.REALITY, trojan.tls.security)
        assertEquals("key2", trojan.tls.reality?.publicKey)
        assertNull(trojan.tls.reality?.shortId)
    }

    @Test
    fun `malformed json reports the line it could not read`() {
        val broken = """
            {
              "outbounds": [
                {"type":"vless",}
              ]
            }
        """.trimIndent()

        val e = assertThrows(MalformedSubscriptionException::class.java) {
            parser.parseDetailed(broken)
        }

        assertEquals(DocumentFormat.JSON, e.format)
        // The trailing comma is on line 3; the parser stops at the `}` that
        // follows it, which is the same line.
        assertEquals(3, e.line)
        assertTrue(parser.parse(broken).isEmpty())
    }

    @Test
    fun `a tagless outbound is named by its address`() {
        val json = """
            {"outbounds":[{"type":"shadowsocks","server":"e.example.com","server_port":8388,
                           "method":"aes-128-gcm","password":"p"}]}
        """.trimIndent()

        assertEquals("e.example.com:8388", parser.parse(json).single().name)
    }

    @Test
    fun `sniff tells a sing-box config from a SIP008 one`() {
        assertTrue(SingBoxJsonSubscriptionParser.sniff("""{"outbounds":[]}"""))
        assertFalse(SingBoxJsonSubscriptionParser.sniff("""{"servers":[]}"""))
        assertFalse(SingBoxJsonSubscriptionParser.sniff("proxies:\n  - name: x"))
        assertFalse(SingBoxJsonSubscriptionParser.sniff("dmxlc3M6Ly8="))
    }

    @Test
    fun `a bare tls block with enabled false is plain`() {
        val json = """
            {"outbounds":[{"type":"vless","tag":"plain","server":"f.example.com",
                           "server_port":80,"uuid":"u","tls":{"enabled":false}}]}
        """.trimIndent()

        val settings = parser.parse(json).single().settings as VlessSettings
        assertEquals(SecurityMode.NONE, settings.tls.security)
        assertNull(settings.transport.host)
    }
}
