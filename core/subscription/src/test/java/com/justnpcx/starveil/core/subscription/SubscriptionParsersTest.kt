package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.Profile
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
import com.justnpcx.starveil.core.subscription.registry.SubscriptionFormatRegistry
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Base64SubscriptionParserTest {

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    private val resolver: (String) -> Profile? = { link ->
        when {
            link.startsWith("vless://") -> Profile(name = "v", type = ProfileType.VLESS, server = "a", port = 1)
            link.startsWith("ss://") -> Profile(name = "s", type = ProfileType.SHADOWSOCKS, server = "b", port = 2)
            else -> null
        }
    }

    @Test
    fun `decodes lines and resolves each share link`() {
        val body = b64("vless://u@a.example:443#n1\nss://x@b.example:8388#n2\n")
        val profiles = Base64SubscriptionParser(resolver).parse(body)
        assertEquals(2, profiles.size)
        assertEquals(ProfileType.VLESS, profiles[0].type)
        assertEquals(ProfileType.SHADOWSOCKS, profiles[1].type)
    }

    @Test
    fun `sniff accepts base64 of share links and rejects plain text`() {
        assertTrue(Base64SubscriptionParser.sniff(b64("vless://u@a.example:443")))
        assertFalse(Base64SubscriptionParser.sniff("proxies:\n  - name: x"))
        assertFalse(Base64SubscriptionParser.sniff("just some words"))
        assertFalse(Base64SubscriptionParser.sniff(""))
    }

    @Test
    fun `invalid base64 yields no profiles`() {
        assertTrue(Base64SubscriptionParser(resolver).parse("!!!! not base64 !!!!").isEmpty())
    }

    @Test
    fun `a line that is not a usable link is counted as dropped`() {
        val body = b64("vless://u@a.example:443#n1\nnot-a-link\nss://x@b.example:8388#n2\n")
        val outcome = Base64SubscriptionParser(resolver).parseDetailed(body)
        assertEquals(2, outcome.profiles.size)
        assertEquals(1, outcome.dropped)
    }

    @Test
    fun `text that does not decode is not a per-node failure`() {
        // The whole body is the wrong format; the registry reports that as a
        // format failure. Counting it as one dropped node would be a lie.
        assertEquals(0, Base64SubscriptionParser(resolver).parseDetailed("!!!! not base64 !!!!").dropped)
    }
}

class ClashYamlSubscriptionParserTest {

    private val clash = """
        port: 7890
        proxies:
          - name: "vless-reality"
            type: vless
            server: r.example.com
            port: 443
            uuid: 11111111-2222-3333-4444-555555555555
            tls: true
            servername: r.example.com
            client-fingerprint: chrome
            reality-opts:
              public-key: PBK
              short-id: ab12
            network: ws
            ws-opts:
              path: /ws
              headers:
                Host: r.example.com
          - name: "trojan-node"
            type: trojan
            server: t.example.com
            port: 8443
            password: s3cret
            network: grpc
            grpc-opts:
              grpc-service-name: gs
          - name: "ss-node"
            type: ss
            server: ss.example.com
            port: 8388
            cipher: 2022-blake3-aes-128-gcm
            password: pw
          - name: "vmess-node"
            type: vmess
            server: v.example.com
            port: 443
            uuid: x
            alterId: 0
            cipher: auto
            network: ws
            ws-opts:
              path: /vm
    """.trimIndent()

    @Test
    fun `maps every protocol this app can build`() {
        val profiles = ClashYamlSubscriptionParser().parse(clash)
        assertEquals(4, profiles.size)

        val vless = profiles.first { it.type == ProfileType.VLESS }
        val vs = vless.settings as VlessSettings
        assertEquals(SecurityMode.REALITY, vs.tls.security)
        assertEquals("PBK", vs.tls.reality?.publicKey)
        assertEquals("ab12", vs.tls.reality?.shortId)
        assertEquals("chrome", vs.tls.fingerprint)
        assertEquals(TransportType.WS, vs.transport.type)
        assertEquals("/ws", vs.transport.path)
        assertEquals("r.example.com", vs.transport.headers["Host"])

        val trojan = profiles.first { it.type == ProfileType.TROJAN }
        val ts = trojan.settings as TrojanSettings
        assertEquals("s3cret", ts.password)
        assertEquals(TransportType.GRPC, ts.transport.type)
        assertEquals("gs", ts.transport.serviceName)

        val ss = profiles.first { it.type == ProfileType.SHADOWSOCKS }
        val ss2 = ss.settings as ShadowsocksSettings
        assertEquals("2022-blake3-aes-128-gcm", ss2.method)

        val vmess = profiles.first { it.type == ProfileType.VMESS }
        val vm = vmess.settings as VmessSettings
        assertEquals("x", vm.uuid)
        assertEquals("auto", vm.security)
        assertEquals(0, vm.alterId)
        assertEquals(TransportType.WS, vm.transport.type)
        assertEquals("/vm", vm.transport.path)
    }

    @Test
    fun `a vless entry without tls is plain, not tls`() {
        // The default used to be TLS, so a plaintext vless node was imported
        // with a handshake it cannot complete.
        val yaml = """
            proxies:
              - name: plain
                type: vless
                server: p.example.com
                port: 80
                uuid: 11111111-2222-3333-4444-555555555555
        """.trimIndent()

        val settings = ClashYamlSubscriptionParser().parse(yaml).single().settings as VlessSettings
        assertEquals(SecurityMode.NONE, settings.tls.security)
    }

    @Test
    fun `a trojan entry without tls still defaults to tls`() {
        // Trojan has no plaintext mode; only vless gained the NONE default.
        val yaml = """
            proxies:
              - name: tj
                type: trojan
                server: t.example.com
                port: 443
                password: pw
        """.trimIndent()

        val settings = ClashYamlSubscriptionParser().parse(yaml).single().settings as TrojanSettings
        assertEquals(SecurityMode.TLS, settings.tls.security)
    }

    @Test
    fun `hysteria2 keeps obfs and its port-hopping range`() {
        val yaml = """
            proxies:
              - name: hy2
                type: hysteria2
                server: h.example.com
                port: 443
                password: pw
                obfs: salamander
                obfs-password: obfspw
                sni: h.example.com
                skip-cert-verify: true
                ports: 20000-30000
        """.trimIndent()

        val settings = ClashYamlSubscriptionParser().parse(yaml).single().settings as Hysteria2Settings
        assertEquals("pw", settings.password)
        assertEquals("salamander", settings.obfs?.type)
        assertEquals("obfspw", settings.obfs?.password)
        assertEquals(SecurityMode.TLS, settings.tls.security)
        assertEquals(true, settings.tls.allowInsecure)
        assertEquals(listOf("20000:30000"), settings.serverPorts)
    }

    @Test
    fun `tuic keeps its congestion control and relay mode`() {
        val yaml = """
            proxies:
              - name: tuic
                type: tuic
                server: u.example.com
                port: 443
                uuid: 11111111-2222-3333-4444-555555555555
                password: pw
                congestion-controller: bbr
                udp-relay-mode: native
                reduce-rtt: true
                sni: u.example.com
                alpn: [h3]
        """.trimIndent()

        val settings = ClashYamlSubscriptionParser().parse(yaml).single().settings as TuicSettings
        assertEquals("pw", settings.password)
        assertEquals(TuicCongestionControl.BBR, settings.congestionControl)
        assertEquals(TuicUdpRelayMode.NATIVE, settings.udpRelayMode)
        assertEquals(true, settings.zeroRttHandshake)
        assertEquals(SecurityMode.TLS, settings.tls.security)
        assertEquals(listOf("h3"), settings.tls.alpn)
    }

    @Test
    fun `sniff detects a clash document`() {
        assertTrue(ClashYamlSubscriptionParser.sniff(clash))
        assertFalse(ClashYamlSubscriptionParser.sniff("vless://u@a.example:443"))
    }

    @Test
    fun `malformed yaml yields no profiles instead of throwing`() {
        assertTrue(ClashYamlSubscriptionParser().parse("proxies: [oops").isEmpty())
        assertTrue(ClashYamlSubscriptionParser().parse("not: clash").isEmpty())
    }

    @Test
    fun `malformed yaml reports the line it could not read`() {
        val broken = "proxies: [oops"

        val e = assertThrows(MalformedSubscriptionException::class.java) {
            ClashYamlSubscriptionParser().parseDetailed(broken)
        }
        assertEquals(DocumentFormat.YAML, e.format)
        assertNotNull("SnakeYAML gives a mark for this failure", e.line)

        // The simple form stays total: callers that only want profiles have
        // nothing useful to do with the exception.
        assertTrue(ClashYamlSubscriptionParser().parse(broken).isEmpty())
    }

    @Test
    fun `an unsupported protocol counts as a dropped node`() {
        // WireGuard over Clash YAML is a real node the subscription offers and
        // this app cannot build from it. Reporting 1 of 2 is the honest summary.
        val yaml = """
            proxies:
              - name: wg
                type: wireguard
                server: w.example.com
                port: 51820
              - name: ok
                type: ss
                server: s.example.com
                port: 8388
                cipher: aes-128-gcm
                password: p
        """.trimIndent()

        val outcome = ClashYamlSubscriptionParser().parseDetailed(yaml)
        assertEquals(1, outcome.profiles.size)
        assertEquals(1, outcome.dropped)
    }
}

class EchSubscriptionTest {

    private val clash = """
        port: 7890
        proxies:
          - name: "cf"
            type: vless
            server: 104.16.1.1
            port: 443
            uuid: 11111111-2222-3333-4444-555555555555
            tls: true
            servername: ff.example.com
            ech-opts:
              enable: true
              query-server-name: cloudflare-ech.com
            network: ws
            ws-opts:
              path: /?ed=2048
    """.trimIndent()

    @Test
    fun `clash ech-opts reaches the model`() {
        val profiles = ClashYamlSubscriptionParser().parse(clash)
        val ech = (profiles.single().settings as VlessSettings).tls.ech
        assertNotNull(ech)
        assertEquals("cloudflare-ech.com", ech!!.queryServerName)
        assertTrue(ech.enabled)
    }

    @Test
    fun `a sing-box ech block reaches the model`() {
        val json = """
            {"outbounds":[{"type":"vless","tag":"cf","server":"104.16.1.1","server_port":443,
            "uuid":"11111111-2222-3333-4444-555555555555",
            "tls":{"enabled":true,"server_name":"ff.example.com",
                   "ech":{"enabled":true,"query_server_name":"cloudflare-ech.com"}},
            "transport":{"type":"ws","path":"/","headers":{"Host":"ff.example.com"}}}]}
        """.trimIndent()
        val profile = SingBoxJsonSubscriptionParser().parse(json).single()
        val ech = (profile.settings as VlessSettings).tls.ech
        assertNotNull(ech)
        assertEquals("cloudflare-ech.com", ech!!.queryServerName)
    }
}

class SingBoxDetourImportTest {

    @Test
    fun `an outbound whose detour names another becomes a chained node`() {
        val json = """
            {"outbounds":[
              {"type":"vless","tag":"front","server":"front.example.com","server_port":443,
               "uuid":"11111111-2222-3333-4444-555555555555",
               "tls":{"enabled":true,"server_name":"front.example.com"}},
              {"type":"vless","tag":"exit","server":"exit.example.com","server_port":443,
               "uuid":"11111111-2222-3333-4444-555555555555",
               "tls":{"enabled":true,"server_name":"exit.example.com"},
               "detour":"front"}]}
        """.trimIndent()

        val profiles = SingBoxJsonSubscriptionParser().parse(json)

        val exit = profiles.single { it.name == "exit" }
        // The reference is stored as the front's business key, not as its tag:
        // tags do not survive this app's own import.
        assertEquals("VLESS|front.example.com|443|front", exit.frontNodeKey)
        assertEquals(null, profiles.single { it.name == "front" }.frontNodeKey)
    }

    @Test
    fun `a detour that points nowhere is not a chain`() {
        val json = """
            {"outbounds":[
              {"type":"vless","tag":"exit","server":"exit.example.com","server_port":443,
               "uuid":"11111111-2222-3333-4444-555555555555",
               "tls":{"enabled":true,"server_name":"exit.example.com"},
               "detour":"missing"}]}
        """.trimIndent()

        val profile = SingBoxJsonSubscriptionParser().parse(json).single()

        assertNull(profile.frontNodeKey)
    }
}

class SubscriptionRegistryOrderTest {

    private val registry = SubscriptionFormatRegistry.default { null }

    @Test
    fun `descriptor order is specific first, fallback last`() {
        assertEquals(
            listOf("clash-yaml", "sing-box-json", "sip008", "base64-lines", "unknown-fallback"),
            registry.all().map { it.format },
        )
    }

    @Test
    fun `resolve picks the right parser per format`() {
        assertTrue(registry.resolve("proxies:\n  - name: x") is ClashYamlSubscriptionParser)
        val b64 = Base64.getEncoder().encodeToString("vless://u@a.example:443".toByteArray())
        assertTrue(registry.resolve(b64) is Base64SubscriptionParser)
        assertTrue(registry.resolve("""{"outbounds":[]}""") is SingBoxJsonSubscriptionParser)
        assertTrue(registry.resolve("""{"servers":[]}""") is Sip008SubscriptionParser)
        assertTrue(registry.resolve("random text") is UnknownSubscriptionParser)
    }
}
