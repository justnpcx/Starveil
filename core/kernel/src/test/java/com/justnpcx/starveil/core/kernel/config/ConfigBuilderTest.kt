package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.ObfsOptions
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.RealityOptions
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RuleMatcher
import com.justnpcx.starveil.core.model.SecurityMode
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import com.justnpcx.starveil.core.model.TlsOptions
import com.justnpcx.starveil.core.model.TransportOptions
import com.justnpcx.starveil.core.model.TransportType
import com.justnpcx.starveil.core.model.TrojanSettings
import com.justnpcx.starveil.core.model.VlessSettings
import com.justnpcx.starveil.core.model.VmessSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigBuilderTest {

    private val builder = ConfigBuilder(ProtocolRegistry.default())

    private fun vlessProfile(uuid: String = "11111111-2222-3333-4444-555555555555") = Profile(
        name = "node",
        type = ProfileType.VLESS,
        server = "r.example.com",
        port = 443,
        settings = VlessSettings(
            uuid = uuid,
            flow = "xtls-rprx-vision",
            tls = TlsOptions(
                security = SecurityMode.REALITY,
                sni = "r.example.com",
                fingerprint = "chrome",
                reality = RealityOptions(
                    publicKey = "PUBKEY",
                    shortId = "ab12",
                    mlKemPublicKey = "MLKEMKEY",
                ),
            ),
            transport = TransportOptions(type = TransportType.XHTTP, path = "/x"),
            mux = com.justnpcx.starveil.core.model.MuxOptions(enabled = true, protocol = "smux"),
        ),
    )

    @Test
    fun `single VLESS config has the expected sing-box shape`() {
        val json = builder.build(vlessProfile())
        assertTrue(json.contains("\"type\":\"vless\""))
        assertTrue(json.contains("\"tag\":\"proxy\""))
        assertTrue(json.contains("\"server\":\"r.example.com\""))
        assertTrue(json.contains("\"server_port\":443"))
        assertTrue(json.contains("\"uuid\":\"11111111-2222-3333-4444-555555555555\""))
        assertTrue(json.contains("\"flow\":\"xtls-rprx-vision\""))
        assertTrue(json.contains("\"reality\":{\"enabled\":true,\"public_key\":\"PUBKEY\",\"short_id\":\"ab12\",\"support_x25519mlkem768\":true}"))
        assertFalse(
            "sing-box decodes with DisallowUnknownFields; an unknown field here fails the whole config",
            json.contains("ml_kem_public_key"),
        )
        assertTrue(json.contains("\"transport\":{\"type\":\"xhttp\",\"path\":\"/x\"}"))
        assertTrue(json.contains("\"multiplex\":{\"enabled\":true,\"protocol\":\"smux\"}"))
        assertTrue(json.contains("\"type\":\"direct\""))
        assertTrue(json.contains("\"type\":\"block\""))
        assertTrue(json.contains("\"final\":\"proxy\""))
    }

    @Test
    fun `REALITY without an ML-KEM key advertises plain X25519 only`() {
        val base = vlessProfile()
        val settings = base.settings as VlessSettings
        val profile = base.copy(
            settings = settings.copy(
                tls = settings.tls.copy(reality = settings.tls.reality?.copy(mlKemPublicKey = null)),
            ),
        )

        val json = builder.build(profile)
        assertTrue(json.contains("\"public_key\":\"PUBKEY\""))
        assertFalse(json.contains("support_x25519mlkem768"))
    }

    @Test
    fun `Trojan config carries password and TLS`() {
        val profile = Profile(
            name = "t",
            type = ProfileType.TROJAN,
            server = "t.example.com",
            port = 8443,
            settings = TrojanSettings(password = "s3cret", tls = TlsOptions.Plain),
        )
        val json = builder.build(profile)
        assertTrue(json.contains("\"type\":\"trojan\""))
        assertTrue(json.contains("\"password\":\"s3cret\""))
        assertTrue(json.contains("\"tls\":{\"enabled\":true}"))
        assertFalse(json.contains("\"transport\""))
    }

    @Test
    fun `Shadowsocks config carries method and password and no tls block`() {
        val profile = Profile(
            name = "ss",
            type = ProfileType.SHADOWSOCKS,
            server = "ss.example.com",
            port = 8388,
            settings = ShadowsocksSettings(method = "2022-blake3-aes-128-gcm", password = "pw"),
        )
        val json = builder.build(profile)
        assertTrue(json.contains("\"type\":\"shadowsocks\""))
        assertTrue(json.contains("\"method\":\"2022-blake3-aes-128-gcm\""))
        assertTrue(json.contains("\"password\":\"pw\""))
        assertFalse(json.contains("\"tls\""))
    }

    @Test
    fun `VMess config carries uuid, security and alter_id`() {
        val profile = Profile(
            name = "vm",
            type = ProfileType.VMESS,
            server = "vm.example.com",
            port = 443,
            settings = VmessSettings(
                uuid = "11111111-2222-3333-4444-555555555555",
                security = "chacha20-poly1305",
                alterId = 4,
                tls = TlsOptions.Plain,
                transport = TransportOptions(type = TransportType.WS, path = "/ws", host = "vm.example.com"),
            ),
        )
        val json = builder.build(profile)
        assertTrue(json.contains("\"type\":\"vmess\""))
        assertTrue(json.contains("\"uuid\":\"11111111-2222-3333-4444-555555555555\""))
        assertTrue(json.contains("\"security\":\"chacha20-poly1305\""))
        assertTrue(json.contains("\"alter_id\":4"))
        assertTrue(json.contains("\"tls\":{\"enabled\":true}"))
        assertTrue(json.contains("\"transport\":{\"type\":\"ws\",\"path\":\"/ws\",\"headers\":{\"Host\":\"vm.example.com\"}}"))
    }

    @Test
    fun `no rules means an empty rules array`() {
        assertTrue(builder.build(vlessProfile()).contains("\"rules\":[]"))
    }

    @Test
    fun `route rules are emitted in order with the union action shape`() {
        val rules = listOf(
            Rule(
                matcher = RuleMatcher(MatcherKind.DOMAIN_SUFFIX, "example.com"),
                action = RuleAction.DIRECT,
                order = 0,
            ),
            Rule(
                matcher = RuleMatcher(MatcherKind.IP_CIDR, "10.0.0.0/8"),
                action = RuleAction.DIRECT,
                order = 1,
            ),
            Rule(
                matcher = RuleMatcher(MatcherKind.DOMAIN_KEYWORD, "ads"),
                action = RuleAction.BLOCK,
                order = 2,
            ),
            Rule(
                matcher = RuleMatcher(MatcherKind.DOMAIN, "only.example"),
                action = RuleAction.PROXY,
                order = 3,
            ),
            Rule(
                matcher = RuleMatcher(MatcherKind.DOMAIN_SUFFIX, "off.example"),
                action = RuleAction.DIRECT,
                order = 4,
                enabled = false,
            ),
        )
        val json = builder.build(vlessProfile(), rules)
        // Order is the routing decision, so it has to survive verbatim.
        assertTrue(
            json.contains(
                "\"rules\":[" +
                    "{\"domain_suffix\":[\"example.com\"],\"action\":\"direct\"}," +
                    "{\"ip_cidr\":[\"10.0.0.0/8\"],\"action\":\"direct\"}," +
                    "{\"domain_keyword\":[\"ads\"],\"action\":\"reject\"}," +
                    "{\"domain\":[\"only.example\"],\"action\":\"route\",\"outbound\":\"proxy\"}]",
            ),
        )
        // A disabled rule is not emitted at all.
        assertFalse(json.contains("off.example"))
    }

    @Test
    fun `a multi-node config points proxy rules at the urltest group`() {
        val rules = listOf(
            Rule(matcher = RuleMatcher(MatcherKind.DOMAIN, "a.example"), action = RuleAction.PROXY),
        )
        val json = builder.build(listOf(vlessProfile(), vlessProfile(uuid = "u2")), rules)
        assertTrue(json.contains("\"action\":\"route\",\"outbound\":\"auto\""))
    }

    @Test
    fun `geoip is never emitted`() {
        // This fork removed the geoip matcher: a rule carrying one fails config
        // construction ("removed in sing-box 1.12.0"), so the kernel would not
        // start. Nothing in the app may produce it.
        val rules = listOf(
            Rule(matcher = RuleMatcher(MatcherKind.DOMAIN_SUFFIX, "example.com"), action = RuleAction.DIRECT),
        )
        val json = builder.build(vlessProfile(), rules)
        assertFalse(json.contains("geoip"))
        assertFalse(json.contains("geosite"))
    }

    @Test
    fun `Hysteria2 config carries password, obfs and bandwidth hints`() {
        val profile = Profile(
            name = "hy2",
            type = ProfileType.HYSTERIA2,
            server = "hy.example.com",
            port = 443,
            settings = Hysteria2Settings(
                password = "user:pass",
                obfs = ObfsOptions(type = "salamander", password = "obfspw"),
                upMbps = 100,
                downMbps = 200,
                tls = TlsOptions(
                    security = SecurityMode.TLS,
                    sni = "hy.example.com",
                    alpn = listOf("h3"),
                    allowInsecure = true,
                ),
            ),
        )
        val json = builder.build(profile)
        assertTrue(json.contains("\"type\":\"hysteria2\""))
        assertTrue(json.contains("\"password\":\"user:pass\""))
        assertTrue(json.contains("\"obfs\":{\"type\":\"salamander\",\"password\":\"obfspw\"}"))
        assertTrue(json.contains("\"up_mbps\":100"))
        assertTrue(json.contains("\"down_mbps\":200"))
        assertTrue(
            json.contains(
                "\"tls\":{\"enabled\":true,\"server_name\":\"hy.example.com\"," +
                    "\"insecure\":true,\"alpn\":[\"h3\"]}",
            ),
        )
        // QUIC: there is no stream transport and no multiplex to emit.
        assertFalse(json.contains("\"transport\""))
        assertFalse(json.contains("\"multiplex\""))
    }

    @Test
    fun `multi-node config builds an auto urltest group`() {
        val profiles = (0 until 3).map { vlessProfile(uuid = "uuid-$it") }
        val json = builder.build(profiles)
        assertTrue(json.contains("\"tag\":\"proxy-0\""))
        assertTrue(json.contains("\"tag\":\"proxy-2\""))
        assertTrue(json.contains("\"type\":\"urltest\""))
        assertTrue(json.contains("\"final\":\"auto\""))
    }

    @Test
    fun `config declares the tun inbound the platform supplies a fd for`() {
        // Without this inbound the kernel never calls PlatformInterface.openTun,
        // so no traffic can flow even though the VPN is up.
        val json = builder.build(vlessProfile())
        assertTrue(json.contains("\"inbounds\":[{\"type\":\"tun\",\"tag\":\"tun-in\""))
        assertTrue(json.contains("\"auto_route\":true"))
    }

    @Test
    fun `dns uses the new server format, not the removed legacy one`() {
        // sing-box 1.14 removed the legacy {"address": ...} DNS server shape:
        // it fails config decode outright.
        val json = builder.build(vlessProfile())
        assertTrue(json.contains("\"servers\":[{\"type\":\"local\",\"tag\":\"local\"}]"))
        assertFalse(json.contains("\"address\":\"local\""))
    }

    @Test
    fun `missing settings fail with a message that carries no credentials`() {
        val profile = Profile(name = "x", type = ProfileType.VLESS, server = "h.example", port = 443)
        try {
            builder.build(profile)
            throw AssertionError("expected ConfigBuildException")
        } catch (e: ConfigBuildException) {
            assertFalse("message must not leak settings", e.message!!.contains("uuid"))
        }
    }

    @Test
    fun `empty profile list is rejected`() {
        try {
            builder.build(emptyList())
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message != null)
        }
    }

    @Test
    fun `generating a 500-node config stays under 100ms`() {
        val profiles = (0 until 500).map { vlessProfile(uuid = "uuid-$it") }
        builder.build(profiles) // warm up JIT
        val start = System.nanoTime()
        val json = builder.build(profiles)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(json.contains("\"tag\":\"proxy-499\""))
        assertTrue("500-node build took ${elapsedMs}ms", elapsedMs < 100)
    }
}
