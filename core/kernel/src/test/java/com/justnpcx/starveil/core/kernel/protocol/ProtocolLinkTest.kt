package com.justnpcx.starveil.core.kernel.protocol

import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.SecurityMode
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import com.justnpcx.starveil.core.model.TransportType
import com.justnpcx.starveil.core.model.TrojanSettings
import com.justnpcx.starveil.core.model.VlessSettings
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessLinkTest {

    @Test
    fun `parses a basic ws + tls link`() {
        val profile = StarveilProtocols.parseVless(
            "vless://uuid-1234@example.com:443?security=tls&sni=example.com&type=ws&path=%2Fws#My%20Node",
        )
        assertNotNull(profile)
        profile!!
        assertEquals("My Node", profile.name)
        assertEquals(ProfileType.VLESS, profile.type)
        assertEquals("example.com", profile.server)
        assertEquals(443, profile.port)
        val s = profile.settings as VlessSettings
        assertEquals("uuid-1234", s.uuid)
        assertEquals(SecurityMode.TLS, s.tls.security)
        assertEquals("example.com", s.tls.sni)
        assertEquals(TransportType.WS, s.transport.type)
        assertEquals("/ws", s.transport.path)
    }

    @Test
    fun `parses REALITY with ML-KEM, XHTTP and vision flow`() {
        val profile = StarveilProtocols.parseVless(
            "vless://11111111-2222-3333-4444-555555555555@r.example.com:443" +
                "?security=reality&pbk=PUBKEY&sid=ab12&fp=chrome&type=xhttp&path=%2Fx" +
                "&flow=xtls-rprx-vision&spx=MLKEMKEY#R",
        )
        assertNotNull(profile)
        val s = profile!!.settings as VlessSettings
        assertEquals(SecurityMode.REALITY, s.tls.security)
        assertEquals("PUBKEY", s.tls.reality?.publicKey)
        assertEquals("ab12", s.tls.reality?.shortId)
        // `spx` is where a REALITY share link carries the ML-KEM public key.
        assertEquals("MLKEMKEY", s.tls.reality?.mlKemPublicKey)
        assertEquals("chrome", s.tls.fingerprint)
        assertEquals(TransportType.XHTTP, s.transport.type)
        assertEquals("xtls-rprx-vision", s.flow)
    }

    @Test
    fun `rejects non-vless, missing uuid and missing port`() {
        assertNull(StarveilProtocols.parseVless("trojan://x@y.example:443"))
        assertNull(StarveilProtocols.parseVless("vless://@host.example:443"))
        assertNull(StarveilProtocols.parseVless("vless://uuid@host.example"))
        assertNull(StarveilProtocols.parseVless("vless://uuid@host.example:0"))
    }

    @Test
    fun `REALITY without a public key is rejected`() {
        assertNull(
            StarveilProtocols.parseVless("vless://uuid@h.example:443?security=reality&sid=ab"),
        )
    }
}

class TrojanLinkTest {

    @Test
    fun `parses password, defaults to TLS, reads grpc transport`() {
        val profile = StarveilProtocols.parseTrojan(
            "trojan://pass%40word@t.example.com:8443?sni=t.example.com&type=grpc&serviceName=gs#T",
        )
        assertNotNull(profile)
        profile!!
        assertEquals(ProfileType.TROJAN, profile.type)
        assertEquals("t.example.com", profile.server)
        assertEquals(8443, profile.port)
        val s = profile.settings as TrojanSettings
        assertEquals("pass@word", s.password)
        assertEquals(SecurityMode.TLS, s.tls.security)
        assertEquals(TransportType.GRPC, s.transport.type)
        assertEquals("gs", s.transport.serviceName)
    }

    @Test
    fun `rejects non-trojan and empty password`() {
        assertNull(StarveilProtocols.parseTrojan("vless://u@h.example:443"))
        assertNull(StarveilProtocols.parseTrojan("trojan://@h.example:443"))
    }
}

class ShadowsocksLinkTest {

    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray())

    @Test
    fun `parses SIP002 link`() {
        val profile = StarveilProtocols.parseShadowsocks(
            "ss://${b64("aes-256-gcm:secret")}@ss.example.com:8388#SS",
        )
        assertNotNull(profile)
        profile!!
        assertEquals(ProfileType.SHADOWSOCKS, profile.type)
        assertEquals("ss.example.com", profile.server)
        assertEquals(8388, profile.port)
        val s = profile.settings as ShadowsocksSettings
        assertEquals("aes-256-gcm", s.method)
        assertEquals("secret", s.password)
    }

    @Test
    fun `parses 2022-blake3 method`() {
        val pw = b64("0123456789abcdef")
        val profile = StarveilProtocols.parseShadowsocks(
            "ss://${b64("2022-blake3-aes-128-gcm:$pw")}@ss.example.com:8388#B3",
        )
        assertNotNull(profile)
        val s = profile!!.settings as ShadowsocksSettings
        assertEquals("2022-blake3-aes-128-gcm", s.method)
        assertEquals(pw, s.password)
    }

    @Test
    fun `parses legacy whole-body base64 link`() {
        val profile = StarveilProtocols.parseShadowsocks(
            "ss://${b64("aes-128-gcm:pw@ss.example.com:8388")}#Legacy",
        )
        assertNotNull(profile)
        val s = profile!!.settings as ShadowsocksSettings
        assertEquals("aes-128-gcm", s.method)
        assertEquals("pw", s.password)
        assertEquals("ss.example.com", profile.server)
    }

    @Test
    fun `rejects malformed links`() {
        assertNull(StarveilProtocols.parseShadowsocks("vless://u@h.example:443"))
        assertNull(StarveilProtocols.parseShadowsocks("ss://${b64("nocolon")}#x"))
        assertNull(StarveilProtocols.parseShadowsocks("ss://not!base64@h.example:8388"))
    }
}

class ProtocolRegistryWiringTest {

    @Test
    fun `M2 protocols resolve by scheme and have real parsers`() {
        val registry = com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry.default()
        assertEquals(ProfileType.VLESS, registry.fromScheme("vless")?.type)
        assertEquals(ProfileType.TROJAN, registry.fromScheme("trojan")?.type)
        assertEquals(ProfileType.SHADOWSOCKS, registry.fromScheme("ss")?.type)

        val vless = registry.get(ProfileType.VLESS)!!
        val parsed = vless.linkParser("vless://u@h.example:443#n")
        assertNotNull(parsed)
        assertTrue(parsed!!.settings is VlessSettings)
    }

    @Test
    fun `unimplemented protocols stay stubs`() {
        val registry = com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry.default()
        val vmess = registry.get(ProfileType.VMESS)!!
        assertNull(vmess.linkParser("vmess://whatever"))
        assertTrue(vmess.configBuilder(com.justnpcx.starveil.core.model.Profile(
            name = "x", type = ProfileType.VMESS, server = "h", port = 1,
        )).isEmpty())
    }
}
