package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.SecurityMode
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import com.justnpcx.starveil.core.model.TransportType
import com.justnpcx.starveil.core.model.TrojanSettings
import com.justnpcx.starveil.core.model.VlessSettings
import com.justnpcx.starveil.core.subscription.registry.SubscriptionFormatRegistry
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
          - name: "unsupported"
            type: vmess
            server: v.example.com
            port: 443
            uuid: x
    """.trimIndent()

    @Test
    fun `maps vless, trojan and ss and skips unsupported types`() {
        val profiles = ClashYamlSubscriptionParser().parse(clash)
        assertEquals(3, profiles.size)

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
