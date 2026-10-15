package com.justnpcx.starveil.core.kernel.protocol

import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.ObfsOptions
import com.justnpcx.starveil.core.kernel.registry.ConfigPlacement
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.SecurityMode
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import com.justnpcx.starveil.core.model.TransportType
import com.justnpcx.starveil.core.model.TrojanSettings
import com.justnpcx.starveil.core.model.TuicCongestionControl
import com.justnpcx.starveil.core.model.TuicSettings
import com.justnpcx.starveil.core.model.TuicUdpRelayMode
import com.justnpcx.starveil.core.model.VlessSettings
import com.justnpcx.starveil.core.model.VmessSettings
import com.justnpcx.starveil.core.model.WireGuardSettings
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

class VmessLinkTest {

    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray())

    @Test
    fun `parses the base64 JSON form`() {
        val json = """{"v":"2","ps":"vm-node","add":"vm.example.com","port":"443",""" +
            """"id":"11111111-2222-3333-4444-555555555555","aid":"0","scy":"auto",""" +
            """"net":"ws","host":"vm.example.com","path":"/ws","tls":"tls",""" +
            """"sni":"vm.example.com","alpn":"h2","fp":"chrome"}"""
        val profile = StarveilProtocols.parseVmess("vmess://${b64(json)}")
        assertNotNull(profile)
        profile!!
        // The label lives in the JSON (`ps`), not in a fragment.
        assertEquals("vm-node", profile.name)
        assertEquals(ProfileType.VMESS, profile.type)
        assertEquals("vm.example.com", profile.server)
        assertEquals(443, profile.port)
        val s = profile.settings as VmessSettings
        assertEquals("11111111-2222-3333-4444-555555555555", s.uuid)
        assertEquals("auto", s.security)
        assertEquals(0, s.alterId)
        assertEquals(SecurityMode.TLS, s.tls.security)
        assertEquals("vm.example.com", s.tls.sni)
        assertEquals(listOf("h2"), s.tls.alpn)
        assertEquals("chrome", s.tls.fingerprint)
        assertEquals(TransportType.WS, s.transport.type)
        assertEquals("/ws", s.transport.path)
    }

    @Test
    fun `parses the URL form`() {
        val profile = StarveilProtocols.parseVmess(
            "vmess://11111111-2222-3333-4444-555555555555@vm.example.com:8443" +
                "?encryption=auto&security=tls&type=grpc&serviceName=gs&sni=vm.example.com#VM",
        )
        assertNotNull(profile)
        profile!!
        assertEquals("VM", profile.name)
        assertEquals(8443, profile.port)
        val s = profile.settings as VmessSettings
        assertEquals(SecurityMode.TLS, s.tls.security)
        assertEquals(TransportType.GRPC, s.transport.type)
        assertEquals("gs", s.transport.serviceName)
    }

    @Test
    fun `reads a non-zero alterId and numeric port from the JSON form`() {
        val json = """{"add":"h.example","port":443,"id":"u","aid":"4","net":"tcp"}"""
        val profile = StarveilProtocols.parseVmess("vmess://${b64(json)}")
        assertNotNull(profile)
        val s = profile!!.settings as VmessSettings
        assertEquals(4, s.alterId)
        // No `scy` in the link — VMess negotiates the cipher itself.
        assertEquals("auto", s.security)
        assertEquals(SecurityMode.NONE, s.tls.security)
    }

    @Test
    fun `rejects malformed links`() {
        assertNull(StarveilProtocols.parseVmess("vless://u@h.example:443"))
        assertNull(StarveilProtocols.parseVmess("vmess://not!base64"))
        // JSON without a port, and with an out-of-range one.
        assertNull(StarveilProtocols.parseVmess("vmess://${b64("""{"add":"h","id":"u"}""")}"))
        assertNull(StarveilProtocols.parseVmess("vmess://${b64("""{"add":"h","port":"0","id":"u"}""")}"))
    }
}

class Hysteria2LinkTest {

    @Test
    fun `parses the URL form, slash and all`() {
        val profile = StarveilProtocols.parseHysteria2(
            "hysteria2://secret@hy.example.com:443/?sni=hy.example.com&insecure=1&alpn=h3#HY2",
        )
        assertNotNull(profile)
        profile!!
        assertEquals("HY2", profile.name)
        assertEquals(ProfileType.HYSTERIA2, profile.type)
        assertEquals("hy.example.com", profile.server)
        // The `/` before the query must not end up in the port.
        assertEquals(443, profile.port)
        val s = profile.settings as Hysteria2Settings
        assertEquals("secret", s.password)
        assertEquals(SecurityMode.TLS, s.tls.security)
        assertEquals("hy.example.com", s.tls.sni)
        assertTrue(s.tls.allowInsecure)
        assertEquals(listOf("h3"), s.tls.alpn)
        assertNull(s.obfs)
    }

    @Test
    fun `parses user-password auth, obfs and bandwidth hints`() {
        val profile = StarveilProtocols.parseHysteria2(
            "hy2://user:pass@hy.example.com:8443" +
                "?obfs=salamander&obfs-password=obfspw&up=100&down=200#N",
        )
        assertNotNull(profile)
        val s = profile!!.settings as Hysteria2Settings
        // sing-box has a single password field, so the whole userinfo goes in.
        assertEquals("user:pass", s.password)
        assertEquals(ObfsOptions(type = "salamander", password = "obfspw"), s.obfs)
        assertEquals(100, s.upMbps)
        assertEquals(200, s.downMbps)
        assertEquals(8443, profile.port)
    }

    @Test
    fun `an unknown obfs type is dropped rather than passed through`() {
        // sing-box rejects the whole config on an unknown obfs enum, which
        // stops the kernel; dropping it fails one connection instead.
        val profile = StarveilProtocols.parseHysteria2(
            "hysteria2://pw@h.example:443?obfs=something-new&obfs-password=x",
        )
        assertNotNull(profile)
        assertNull((profile!!.settings as Hysteria2Settings).obfs)
    }

    @Test
    fun `obfs without a password is dropped`() {
        val profile = StarveilProtocols.parseHysteria2(
            "hysteria2://pw@h.example:443?obfs=salamander",
        )
        assertNotNull(profile)
        assertNull((profile!!.settings as Hysteria2Settings).obfs)
    }

    @Test
    fun `rejects malformed links`() {
        assertNull(StarveilProtocols.parseHysteria2("vless://u@h.example:443"))
        assertNull(StarveilProtocols.parseHysteria2("hysteria2://@h.example:443"))
        assertNull(StarveilProtocols.parseHysteria2("hysteria2://pw@h.example"))
    }
}

class TuicLinkTest {

    @Test
    fun `parses uuid, password, congestion control and TLS`() {
        val profile = StarveilProtocols.parseTuic(
            "tuic://11111111-2222-3333-4444-555555555555:secret@t.example.com:443" +
                "?congestion_control=bbr&udp_relay_mode=native&alpn=h3&sni=t.example.com" +
                "&allow_insecure=1#TUIC",
        )
        assertNotNull(profile)
        profile!!
        assertEquals("TUIC", profile.name)
        assertEquals(ProfileType.TUIC, profile.type)
        assertEquals("t.example.com", profile.server)
        assertEquals(443, profile.port)
        val s = profile.settings as TuicSettings
        assertEquals("11111111-2222-3333-4444-555555555555", s.uuid)
        assertEquals("secret", s.password)
        assertEquals(TuicCongestionControl.BBR, s.congestionControl)
        assertEquals(TuicUdpRelayMode.NATIVE, s.udpRelayMode)
        assertEquals(SecurityMode.TLS, s.tls.security)
        assertEquals("t.example.com", s.tls.sni)
        assertEquals(listOf("h3"), s.tls.alpn)
        // TUIC links spell this one with underscores.
        assertTrue(s.tls.allowInsecure)
    }

    @Test
    fun `an unknown congestion control or relay mode is dropped`() {
        val profile = StarveilProtocols.parseTuic(
            "tuic://uuid:pw@t.example:443?congestion_control=vegas&udp_relay_mode=weird",
        )
        assertNotNull(profile)
        val s = profile!!.settings as TuicSettings
        // Both are enums upstream: an unknown value would fail the whole
        // config and stop the kernel, so they are dropped instead.
        assertNull(s.congestionControl)
        assertNull(s.udpRelayMode)
    }

    @Test
    fun `rejects links without both halves of the userinfo`() {
        assertNull(StarveilProtocols.parseTuic("vless://u@h.example:443"))
        assertNull(StarveilProtocols.parseTuic("tuic://justapassword@t.example:443"))
        assertNull(StarveilProtocols.parseTuic("tuic://uuid:@t.example:443"))
        assertNull(StarveilProtocols.parseTuic("tuic://uuid:pw@t.example"))
    }
}

class WireGuardConfTest {

    private val conf = """
        # a wg-quick config
        [Interface]
        PrivateKey = aPrivateKeyBase64=
        Address = 10.7.0.2/32, fd00::2/128
        DNS = 1.1.1.1
        MTU = 1420
        ListenPort = 51820

        [Peer]
        PublicKey = aPublicKeyBase64=
        PresharedKey = aPresharedKeyBase64=
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = wg.example.com:51820
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun `parses a wg-quick conf`() {
        val profile = StarveilProtocols.parseWireGuardConf(conf)
        assertNotNull(profile)
        profile!!
        assertEquals(ProfileType.WIREGUARD, profile.type)
        assertEquals("wg.example.com", profile.server)
        assertEquals(51820, profile.port)
        val s = profile.settings as WireGuardSettings
        assertEquals("aPrivateKeyBase64=", s.privateKey)
        assertEquals(listOf("10.7.0.2/32", "fd00::2/128"), s.address)
        assertEquals(1420, s.mtu)
        assertEquals(51820, s.listenPort)
        val peer = s.peers.single()
        assertEquals("aPublicKeyBase64=", peer.publicKey)
        assertEquals("wg.example.com", peer.endpointAddress)
        assertEquals(51820, peer.endpointPort)
        assertEquals(listOf("0.0.0.0/0", "::/0"), peer.allowedIps)
        assertEquals("aPresharedKeyBase64=", peer.preSharedKey)
        assertEquals(25, peer.persistentKeepalive)
    }

    @Test
    fun `keeps every peer and skips one with no endpoint`() {
        val text = """
            [Interface]
            PrivateKey = k
            Address = 10.7.0.2/32

            [Peer]
            PublicKey = p1
            Endpoint = a.example.com:51820

            [Peer]
            PublicKey = p2-no-endpoint

            [Peer]
            PublicKey = p3
            Endpoint = b.example.com:51821
        """.trimIndent()
        val settings = StarveilProtocols.parseWireGuardConf(text)!!.settings as WireGuardSettings
        // The middle peer cannot be dialled, so it is dropped rather than
        // failing the whole import.
        assertEquals(listOf("p1", "p3"), settings.peers.map { it.publicKey })
    }

    @Test
    fun `rejects a conf with no interface, key, address or peer`() {
        assertNull(StarveilProtocols.parseWireGuardConf("not a conf at all"))
        assertNull(StarveilProtocols.parseWireGuardConf("[Interface]\nAddress = 10.0.0.2/32"))
        assertNull(StarveilProtocols.parseWireGuardConf("[Interface]\nPrivateKey = k"))
        assertNull(
            StarveilProtocols.parseWireGuardConf("[Interface]\nPrivateKey = k\nAddress = 10.0.0.2/32"),
        )
    }
}

class ProtocolRegistryWiringTest {

    @Test
    fun `real protocols resolve by scheme and have real parsers`() {
        val registry = com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry.default()
        assertEquals(ProfileType.VLESS, registry.fromScheme("vless")?.type)
        assertEquals(ProfileType.TROJAN, registry.fromScheme("trojan")?.type)
        assertEquals(ProfileType.SHADOWSOCKS, registry.fromScheme("ss")?.type)
        assertEquals(ProfileType.VMESS, registry.fromScheme("vmess")?.type)
        assertEquals(ProfileType.HYSTERIA2, registry.fromScheme("hy2")?.type)
        assertEquals(ProfileType.HYSTERIA2, registry.fromScheme("hysteria2")?.type)
        assertEquals(ProfileType.TUIC, registry.fromScheme("tuic")?.type)
        assertEquals(ProfileType.WIREGUARD, registry.fromScheme("wireguard")?.type)

        val vless = registry.get(ProfileType.VLESS)!!
        val parsed = vless.linkParser("vless://u@h.example:443#n")
        assertNotNull(parsed)
        assertTrue(parsed!!.settings is VlessSettings)
    }

    @Test
    fun `no protocol is left as a stub`() {
        val registry = com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry.default()
        // OTHER is the placeholder for future protocols, not a protocol.
        ProfileType.entries.filter { it != ProfileType.OTHER }.forEach { type ->
            assertNotNull("no descriptor registered for $type", registry.get(type))
        }
    }

    @Test
    fun `WireGuard is claimed by sniffing and placed as an endpoint`() {
        val descriptor =
            com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry.default()
                .get(ProfileType.WIREGUARD)!!
        // It has no usable URI scheme, so a .conf is recognised by its sections.
        assertTrue(descriptor.sniff("[Interface]\nPrivateKey = k\n[Peer]\nPublicKey = p"))
        assertFalse(descriptor.sniff("vless://u@h.example:443"))
        // sing-box 1.13 removed the WireGuard outbound.
        assertEquals(ConfigPlacement.ENDPOINT, descriptor.placement)
    }
}
