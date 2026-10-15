package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.ObfsOptions
import com.justnpcx.starveil.core.model.GroupMode
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
import com.justnpcx.starveil.core.model.TuicCongestionControl
import com.justnpcx.starveil.core.model.TuicSettings
import com.justnpcx.starveil.core.model.TuicUdpRelayMode
import com.justnpcx.starveil.core.model.VlessSettings
import com.justnpcx.starveil.core.model.VmessSettings
import com.justnpcx.starveil.core.model.XhttpMode
import com.justnpcx.starveil.core.model.WireGuardPeer
import com.justnpcx.starveil.core.model.WireGuardSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
                    mlKem = true,
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
    fun `xhttp emits the mode the link asked for`() {
        val base = vlessProfile()
        val settings = base.settings as VlessSettings
        val profile = base.copy(
            settings = settings.copy(
                transport = settings.transport.copy(mode = XhttpMode.STREAM_UP),
            ),
        )

        val json = builder.build(profile)

        // The shape test above pins the other half: with no mode there is no
        // `mode` key at all, which is what leaves the core to choose.
        assertTrue(
            json.contains("\"transport\":{\"type\":\"xhttp\",\"path\":\"/x\",\"mode\":\"stream-up\"}"),
        )
    }

    @Test
    fun `REALITY without an ML-KEM key advertises plain X25519 only`() {
        val base = vlessProfile()
        val settings = base.settings as VlessSettings
        val profile = base.copy(
            settings = settings.copy(
                tls = settings.tls.copy(reality = settings.tls.reality?.copy(mlKem = false)),
            ),
        )

        val json = builder.build(profile)
        assertTrue(json.contains("\"public_key\":\"PUBKEY\""))
        assertFalse(json.contains("support_x25519mlkem768"))
    }

    @Test
    fun `xhttp carries the host as a Host header`() {
        // xhttp, like ws, has no `host` field in sing-box — the host travels in
        // the headers. Emitting a `host` key would be rejected outright, since
        // options decode with DisallowUnknownFields.
        val base = vlessProfile()
        val settings = base.settings as VlessSettings
        val profile = base.copy(
            settings = settings.copy(
                transport = TransportOptions(type = TransportType.XHTTP, path = "/x", host = "h.example"),
            ),
        )

        val json = builder.build(profile)

        assertTrue(
            json.contains("\"transport\":{\"type\":\"xhttp\",\"path\":\"/x\",\"headers\":{\"Host\":\"h.example\"}}"),
        )
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
    fun `TUIC config carries uuid, password and the QUIC knobs`() {
        val profile = Profile(
            name = "tuic",
            type = ProfileType.TUIC,
            server = "t.example.com",
            port = 443,
            settings = TuicSettings(
                uuid = "11111111-2222-3333-4444-555555555555",
                password = "secret",
                congestionControl = TuicCongestionControl.BBR,
                udpRelayMode = TuicUdpRelayMode.NATIVE,
                zeroRttHandshake = true,
                tls = TlsOptions(
                    security = SecurityMode.TLS,
                    sni = "t.example.com",
                    alpn = listOf("h3"),
                ),
            ),
        )
        val json = builder.build(profile)
        assertTrue(json.contains("\"type\":\"tuic\""))
        assertTrue(json.contains("\"uuid\":\"11111111-2222-3333-4444-555555555555\""))
        assertTrue(json.contains("\"password\":\"secret\""))
        assertTrue(json.contains("\"congestion_control\":\"bbr\""))
        assertTrue(json.contains("\"udp_relay_mode\":\"native\""))
        assertTrue(json.contains("\"zero_rtt_handshake\":true"))
        assertTrue(
            json.contains(
                "\"tls\":{\"enabled\":true,\"server_name\":\"t.example.com\",\"alpn\":[\"h3\"]}",
            ),
        )
        // QUIC: no stream transport and no multiplex.
        assertFalse(json.contains("\"transport\""))
        assertFalse(json.contains("\"multiplex\""))
    }

    @Test
    fun `WireGuard goes in endpoints, not outbounds`() {
        val profile = Profile(
            name = "wg",
            type = ProfileType.WIREGUARD,
            server = "wg.example.com",
            port = 51820,
            settings = WireGuardSettings(
                privateKey = "priv",
                address = listOf("10.7.0.2/32"),
                peers = listOf(
                    WireGuardPeer(
                        publicKey = "pub",
                        endpointAddress = "wg.example.com",
                        endpointPort = 51820,
                        allowedIps = listOf("0.0.0.0/0"),
                        preSharedKey = "psk",
                        persistentKeepalive = 25,
                    ),
                ),
                mtu = 1420,
            ),
        )
        val json = builder.build(profile)
        // sing-box 1.13 removed the WireGuard outbound: under `outbounds` the
        // config fails construction ("use WireGuard endpoint instead").
        assertTrue(json.contains("\"endpoints\":[{\"type\":\"wireguard\""))
        assertFalse(json.contains("\"outbounds\":[{\"type\":\"wireguard\""))
        assertTrue(json.contains("\"private_key\":\"priv\""))
        assertTrue(json.contains("\"address\":[\"10.7.0.2/32\"]"))
        assertTrue(json.contains("\"mtu\":1420"))
        assertTrue(
            json.contains(
                "\"peers\":[{\"address\":\"wg.example.com\",\"port\":51820," +
                    "\"public_key\":\"pub\",\"pre_shared_key\":\"psk\"," +
                    "\"allowed_ips\":[\"0.0.0.0/0\"],\"persistent_keepalive_interval\":25}]",
            ),
        )
        // The route still points at the endpoint's tag — the outbound manager
        // resolves an endpoint tag as well.
        assertTrue(json.contains("\"final\":\"proxy\""))
        assertTrue(json.contains("\"type\":\"direct\""))
    }

    @Test
    fun `the blocking config carries no node and rejects everything`() {
        val json = builder.buildBlocking()
        // This is what the Kill Switch runs: the route's final is the built-in
        // block outbound, so nothing can leave.
        assertTrue(json.contains("\"final\":\"block\""))
        assertTrue(json.contains("\"type\":\"block\""))
        // No proxy of any kind, so no credential is involved.
        assertFalse(json.contains("\"type\":\"vless\""))
        assertFalse(json.contains("\"endpoints\""))
        assertFalse(json.contains("\"uuid\""))
        // The tun still has to come up, or there is nothing to block with.
        assertTrue(json.contains("\"inbounds\":[{\"type\":\"tun\""))
    }

    @Test
    fun `a config with no endpoint omits the endpoints key entirely`() {
        assertFalse(builder.build(vlessProfile()).contains("\"endpoints\""))
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
    fun `a selector group lists its members and picks a starting one`() {
        val profiles = (0 until 3).map { vlessProfile(uuid = "uuid-$it") }
        val json = builder.build(profiles, mode = GroupMode.SELECTOR)

        assertTrue(json.contains("\"type\":\"selector\""))
        assertTrue(json.contains("\"final\":\"auto\""))
        // A selector has to start on something, and it must be a member it
        // actually lists — sing-box rejects a default that is not in outbounds.
        assertTrue(json.contains("\"default\":\"proxy-0\""))
        assertTrue(json.contains("\"outbounds\":[\"proxy-0\",\"proxy-1\",\"proxy-2\"]"))
        // The urltest-only knobs must not leak into a selector.
        assertFalse(json.contains("\"tolerance\""))
        assertFalse(json.contains("\"interval\""))
    }

    @Test
    fun `a manual group is rejected because it is a folder, not a connection target`() {
        // Callers are expected to connect to a member instead. Failing loudly
        // beats emitting a config that silently does something else.
        val error = assertThrows(IllegalArgumentException::class.java) {
            builder.build(listOf(vlessProfile()), mode = GroupMode.MANUAL)
        }
        assertTrue(error.message.orEmpty().contains("MANUAL"))
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

    /**
     * A guard against an accidental super-linear rewrite, not a benchmark.
     *
     * Building 500 nodes is linear work that measures single-digit
     * milliseconds; a per-node scan over all nodes would take seconds. The
     * bound is deliberately far above the real cost, because this runs
     * alongside every other module's tests and a wall-clock assertion tight
     * enough to be a useful benchmark is a flake generator — one GC pause, or
     * a JIT that never got a compiler thread, is enough to fail a correct
     * change. That is not hypothetical: with a single warm-up and a single
     * measurement this failed once under load and passed in isolation.
     *
     * Best-of-N after a real warm-up for the same reason: the fastest run is
     * the one least polluted by whatever else the machine was doing.
     */
    @Test
    fun `generating a 500-node config stays linear`() {
        val profiles = (0 until 500).map { vlessProfile(uuid = "uuid-$it") }
        repeat(WARMUP_RUNS) { builder.build(profiles) }

        val fastest = (1..MEASURED_RUNS).minOf {
            val start = System.nanoTime()
            val json = builder.build(profiles)
            assertTrue(json.contains("\"tag\":\"proxy-499\""))
            (System.nanoTime() - start) / 1_000_000
        }

        assertTrue(
            "fastest of $MEASURED_RUNS builds of 500 nodes was ${fastest}ms",
            fastest < BUDGET_MS,
        )
    }

    private companion object {
        /** ~4ms measured; the headroom is for a loaded machine, not for drift. */
        const val BUDGET_MS = 250L

        /** Enough for C1/C2 to compile the hot path before anything is timed. */
        const val WARMUP_RUNS = 5
        const val MEASURED_RUNS = 5
    }
}
