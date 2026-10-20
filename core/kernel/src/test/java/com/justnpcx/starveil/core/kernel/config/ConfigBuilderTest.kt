package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.InstalledRulePack
import com.justnpcx.starveil.core.model.LocalApiConfig
import com.justnpcx.starveil.core.model.MainlandRuleSets
import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.ObfsOptions
import com.justnpcx.starveil.core.model.PerAppOutbound
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.RealityOptions
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RuleMatcher
import com.justnpcx.starveil.core.model.RulePack
import com.justnpcx.starveil.core.model.RulePackAction
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
    fun `xhttp emits the extra fields the core reads, and nothing else`() {
        val base = vlessProfile()
        val settings = base.settings as VlessSettings
        val profile = base.copy(
            settings = settings.copy(
                transport = settings.transport.copy(
                    uplinkHttpMethod = "PUT",
                    noGrpcHeader = true,
                    xPaddingBytes = "100-1000",
                    xPaddingKey = "x_pad",
                    xPaddingHeader = "X-Padding",
                    downloadSettings = com.justnpcx.starveil.core.model.XhttpDownloadSettings(
                        server = "down.example.com",
                        port = 8443,
                        path = "/down",
                    ),
                ),
            ),
        )

        val json = builder.build(profile)

        assertTrue(json.contains("\"uplink_http_method\":\"PUT\""))
        assertTrue(json.contains("\"no_grpc_header\":true"))
        assertTrue(json.contains("\"x_padding_bytes\":\"100-1000\""))
        assertTrue(json.contains("\"x_padding_key\":\"x_pad\""))
        assertTrue(json.contains("\"x_padding_header\":\"X-Padding\""))
        assertTrue(
            json.contains(
                "\"download_settings\":{\"server\":\"down.example.com\",\"port\":8443,\"path\":\"/down\"}",
            ),
        )
        // Not in option.V2RayXHTTPOptions: writing it would make the kernel
        // reject the whole config, not ignore one field.
        assertFalse(json.contains("xmux"))
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
    fun `with no user rules only the local network rule is emitted`() {
        // Not an empty array any more: private and multicast addresses go
        // direct in every config, because a proxied router is an unreachable
        // one.
        val json = builder.build(vlessProfile())
        assertTrue(
            json.contains(
                "\"rules\":[{\"ip_cidr\":[\"10.0.0.0/8\",\"172.16.0.0/12\",\"192.168.0.0/16\"," +
                    "\"127.0.0.0/8\",\"::1/128\",\"fc00::/7\",\"fe80::/10\",\"224.0.0.0/4\"]," +
                    "\"action\":\"route\",\"outbound\":\"direct\"}]",
            ),
        )
    }

    @Test
    fun `route rules are emitted in order, each naming its outbound`() {
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
                    "{\"ip_cidr\":[\"10.0.0.0/8\",\"172.16.0.0/12\",\"192.168.0.0/16\"," +
                    "\"127.0.0.0/8\",\"::1/128\",\"fc00::/7\",\"fe80::/10\",\"224.0.0.0/4\"]," +
                    "\"action\":\"route\",\"outbound\":\"direct\"}," +
                    "{\"domain_suffix\":[\"example.com\"],\"action\":\"route\",\"outbound\":\"direct\"}," +
                    "{\"ip_cidr\":[\"10.0.0.0/8\"],\"action\":\"route\",\"outbound\":\"direct\"}," +
                    "{\"domain_keyword\":[\"ads\"],\"action\":\"reject\"}," +
                    "{\"domain\":[\"only.example\"],\"action\":\"route\",\"outbound\":\"proxy\"}]",
            ),
        )
        // A disabled rule is not emitted at all.
        assertFalse(json.contains("off.example"))
    }

    @Test
    fun `a direct rule names the direct outbound instead of the direct action`() {
        // `{"action":"direct"}` is a *DNS* rule action. sing-box accepts it in
        // a route rule, matches it, logs `=> direct`, and then does not select
        // it — so the connection falls through to `final` and a rule that was
        // meant to keep traffic at home silently proxies it. Verified against
        // the real kernel, which logged
        //     router: match[0] domain=www.baidu.com => direct
        //     outbound/socks[proxy]: outbound connection to www.baidu.com:443
        val rules = listOf(
            Rule(matcher = RuleMatcher(MatcherKind.DOMAIN, "keep.example"), action = RuleAction.DIRECT),
        )
        val json = builder.build(vlessProfile(), rules)

        assertTrue(json.contains("\"action\":\"route\",\"outbound\":\"direct\""))
        assertFalse(
            "the bare `direct` action is silently ignored by the router",
            json.contains("\"action\":\"direct\""),
        )
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
    fun `Hysteria2 port hopping is emitted as server_ports and hop_interval`() {
        val profile = Profile(
            name = "hy2 hop",
            type = ProfileType.HYSTERIA2,
            server = "hy.example.com",
            port = 443,
            settings = Hysteria2Settings(
                password = "pw",
                tls = TlsOptions(security = SecurityMode.TLS, sni = "hy.example.com"),
                serverPorts = listOf("20000:30000"),
                hopInterval = "15s",
            ),
        )
        val json = builder.build(profile)
        assertTrue(json.contains("\"server_ports\":[\"20000:30000\"]"))
        assertTrue(json.contains("\"hop_interval\":\"15s\""))
        // The advertised port is kept: it is the fallback when hopping fails.
        assertTrue(json.contains("\"server_port\":443"))
    }

    @Test
    fun `Hysteria2 disables the Chrome QUIC parrot`() {
        // 1.14 defaults ChromeParrot on. Measured on device, it halves HY2
        // throughput on a normal node; the 1.12 core NekoBox ships has no
        // such behaviour and was 1.3-2x faster because of it.
        val profile = Profile(
            name = "hy2 parrot",
            type = ProfileType.HYSTERIA2,
            server = "hy.example.com",
            port = 443,
            settings = Hysteria2Settings(
                password = "pw",
                tls = TlsOptions(security = SecurityMode.TLS),
            ),
        )
        val json = builder.build(profile)
        assertTrue(json.contains("\"disable_chrome_parrot\":true"))
    }

    @Test
    fun `Hysteria2 without a range emits no hopping keys`() {
        val profile = Profile(
            name = "hy2 plain",
            type = ProfileType.HYSTERIA2,
            server = "hy.example.com",
            port = 443,
            settings = Hysteria2Settings(
                password = "pw",
                tls = TlsOptions(security = SecurityMode.TLS),
            ),
        )
        val json = builder.build(profile)
        assertFalse(json.contains("server_ports"))
        assertFalse(json.contains("hop_interval"))
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
        assertTrue(json.contains("\"strategy\":\"ipv4_only\",\"servers\":[{\"type\":\"local\",\"tag\":\"local\"}]"))
        assertFalse(json.contains("\"address\":\"local\""))
    }

    @Test
    fun `the dns cache flag is never put on a server`() {
        // `optimistic` belongs to the dns block. On a server it is not a field
        // sing-box knows, and the config is rejected before anything starts:
        //     dns.servers[0].optimistic: json: unknown field "optimistic"
        // That was true of the preset-off config too, so this guard covers
        // both shapes.
        val without = builder.build(vlessProfile())
        val withPreset = builder.build(listOf(vlessProfile()), mainland = mainland, preset = RoutePreset.BYPASS_MAINLAND)

        assertFalse("preset-off config must decode", without.contains("\"optimistic\""))
        assertEquals(
            "one flag, on the dns block",
            1,
            Regex("\"optimistic\":true").findAll(withPreset).count(),
        )
        assertTrue(withPreset.contains("\"final\":\"dns-proxy\",\"optimistic\":true"))
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

        // Printed as well as asserted: the budget is deliberately loose (see
        // above), so the number itself is the thing worth reading, and a
        // failing build should not be the only way to learn it.
        println("CONFIGBUILD_500_NODES_MS=$fastest")

        assertTrue(
            "fastest of $MEASURED_RUNS builds of 500 nodes was ${fastest}ms",
            fastest < BUDGET_MS,
        )
    }

    // ---- the mainland preset ----

    private val mainland = MainlandRuleSets(
        geositeCnPath = "/data/user/0/app/files/rulesets/geosite-cn.srs",
        geoipCnPath = "/data/user/0/app/files/rulesets/geoip-cn.srs",
    )

    @Test
    fun `the preset declares its rule sets as local files`() {
        val json = builder.build(listOf(vlessProfile()), mainland = mainland, preset = RoutePreset.BYPASS_MAINLAND)

        // Local, not remote. A remote set is fetched during start() and the
        // router fails the whole start when that fetch fails, which is the
        // deadlock the preset exists to avoid.
        assertTrue(json.contains("\"type\":\"local\",\"tag\":\"geosite-cn\""))
        assertTrue(json.contains("\"type\":\"local\",\"tag\":\"geoip-cn\""))
        assertTrue(json.contains("/files/rulesets/geosite-cn.srs"))
        assertTrue(json.contains("/files/rulesets/geoip-cn.srs"))
        assertTrue(json.contains("\"format\":\"binary\""))
        assertFalse("a remote set would fetch during start", json.contains("\"type\":\"remote\""))
    }

    @Test
    fun `the preset rules sit after the user's and before final`() {
        val rules = listOf(
            Rule(
                matcher = RuleMatcher(MatcherKind.DOMAIN_SUFFIX, "example.com"),
                action = RuleAction.PROXY,
            ),
        )
        val json = builder.build(listOf(vlessProfile()), rules, mainland = mainland, preset = RoutePreset.BYPASS_MAINLAND)

        val user = json.indexOf("example.com")
        val geosite = json.indexOf("\"rule_set\":\"geosite-cn\",\"action\":\"route\",\"outbound\":\"direct\"")
        val geoip = json.indexOf("\"rule_set\":\"geoip-cn\",\"action\":\"route\",\"outbound\":\"direct\"")
        val final = json.indexOf("\"final\":\"auto\"")

        assertTrue("user rule is present", user >= 0)
        assertTrue("geosite rule is present", geosite >= 0)
        assertTrue("geoip rule is present", geoip >= 0)
        // The user's own rule wins, so an exception can be carved out of the
        // preset; the preset then wins over the default.
        assertTrue("user rule comes first", user < geosite)
        assertTrue("geosite before geoip", geosite < geoip)
        assertTrue("preset rules come before final", geoip < final)
    }

    @Test
    fun `without the preset nothing about routing changes`() {
        val without = builder.build(listOf(vlessProfile()))
        val withPreset = builder.build(listOf(vlessProfile()), mainland = mainland, preset = RoutePreset.BYPASS_MAINLAND)

        // The degrade path: the app could not get the files, so the config is
        // exactly what it was before the preset existed.
        assertFalse(without.contains("rule_set"))
        assertFalse(without.contains("geosite-cn"))
        assertFalse(without.contains("dns-cn"))
        assertTrue(withPreset.contains("geosite-cn"))
    }

    @Test
    fun `the preset keeps LAN names on the device and domestic names domestic`() {
        val json = builder.build(listOf(vlessProfile()), mainland = mainland, preset = RoutePreset.BYPASS_MAINLAND)

        // A LAN name is settled before anything is resolved — the only way to
        // keep the question from leaving the device. It comes first because it
        // is the cheapest and most specific rule there is.
        val lan = json.indexOf("\"domain_suffix\":[\".lan\",\".local\"")
        val geosite = json.indexOf("\"rule_set\":\"geosite-cn\",\"action\":\"route\",\"server\":\"dns-cn\"")
        assertTrue(lan >= 0)
        assertTrue(geosite >= 0)
        assertTrue("LAN names are matched first", lan < geosite)

        // Domestic names go to a domestic resolver, direct: a poisoned answer
        // would resolve outside geoip-cn and send domestic traffic abroad.
        assertTrue(json.contains("223.5.5.5"))
        // Not by naming the `direct` outbound — it carries no options, and
        // sing-box refuses the pairing at start with "detour to an empty direct
        // outbound makes no sense". Left unset, the dialer falls back to the
        // default outbound, which is that same direct one.
        assertFalse(json.contains("\"detour\":\"direct\""))

        // The private-address check is a response match now, not a Legacy
        // Address Filter. That form resolved through `local` first, rejected
        // every public answer and resolved again on the rule that actually
        // applied — two lookups for an answer that was thrown away — and 1.16
        // removes it outright.
        assertTrue(json.contains("\"action\":\"evaluate\",\"server\":\"dns-proxy\""))
        assertTrue(json.contains("\"match_response\":true,\"ip_is_private\":true"))
        assertEquals(
            "the only ip_is_private is the response-matched one",
            1,
            Regex("\"ip_is_private\":true").findAll(json).count(),
        )

        // Everything else resolves through the tunnel.
        assertTrue(json.contains("\"final\":\"dns-proxy\""))
    }

    @Test
    fun `the proxy resolver detours through the tag this build defines`() {
        // A single-node build defines `proxy`; a group build defines `auto`.
        // Naming one the config does not define fails the start with
        // "outbound detour not found: auto" — which is what a hardcoded `auto`
        // did for every single-node connection.
        val single = builder.build(vlessProfile(), mainland = mainland, preset = RoutePreset.BYPASS_MAINLAND)
        val group = builder.build(listOf(vlessProfile()), mainland = mainland, preset = RoutePreset.BYPASS_MAINLAND)

        assertTrue(single.contains("\"tag\":\"dns-proxy\",\"server\":\"1.1.1.1\",\"detour\":\"proxy\""))
        assertTrue(group.contains("\"tag\":\"dns-proxy\",\"server\":\"1.1.1.1\",\"detour\":\"auto\""))
    }

    @Test
    fun `the preset asks for a default domain resolver and the plain config does not`() {
        // With one DNS server the kernel resolves an outbound's own address
        // with it and says nothing; with two or more it will not choose, and
        // refuses the start with "missing route.default_domain_resolver". The
        // preset takes the count from one to three.
        val single = builder.build(vlessProfile(), mainland = mainland, preset = RoutePreset.BYPASS_MAINLAND)
        val without = builder.build(vlessProfile())

        assertTrue(single.contains("\"default_domain_resolver\":\"local\""))
        assertFalse(without.contains("default_domain_resolver"))
    }

    // ---- per-app routing ----

    @Test
    fun `global proxy is the absence of preset rules`() {
        val json = builder.build(vlessProfile(), preset = RoutePreset.GLOBAL_PROXY)

        assertTrue(json.contains("\"final\":\"proxy\""))
        assertFalse(json.contains("rule_set"))
    }

    @Test
    fun `global direct sends everything to the direct outbound`() {
        val json = builder.build(vlessProfile(), preset = RoutePreset.GLOBAL_DIRECT)

        assertTrue(json.contains("\"final\":\"direct\""))
        // The node is still in the config: switching the policy back must not
        // need a different profile.
        assertTrue(json.contains("\"tag\":\"proxy\""))
        assertFalse(json.contains("rule_set"))
    }

    @Test
    fun `blocking ads adds a reject rule before everything else`() {
        val json = builder.build(
            vlessProfile(),
            mainland = mainland,
            preset = RoutePreset.BYPASS_MAINLAND,
            adsPath = "/files/rulesets/geosite-ads.srs",
        )
        // Start at the route block: the DNS rules above it also mention
        // geosite-cn, and comparing indices across the two would be nonsense.
        val route = json.substringAfter("\"route\":{").substringAfter("\"rules\":[")
        val ads = route.indexOf("\"rule_set\":\"ads\",\"action\":\"reject\"")
        val domestic = route.indexOf("\"rule_set\":\"geosite-cn\"")

        assertTrue(ads >= 0)
        assertTrue("ads come before the preset", ads < domestic)
        // And the set is declared, or the rule would reference nothing.
        assertTrue(json.contains("\"tag\":\"ads\""))
    }

    @Test
    fun `ads stack on global direct too`() {
        val json = builder.build(
            vlessProfile(),
            preset = RoutePreset.GLOBAL_DIRECT,
            adsPath = "/files/rulesets/geosite-ads.srs",
        )
        assertTrue(json.contains("\"final\":\"direct\""))
        assertTrue(json.contains("\"rule_set\":\"ads\",\"action\":\"reject\""))
    }

    @Test
    fun `a subscribed pack is declared and its rule is emitted in order`() {
        val json = builder.build(
            vlessProfile(),
            rules = listOf(
                Rule(
                    matcher = RuleMatcher(MatcherKind.DOMAIN_SUFFIX, "example.com"),
                    action = RuleAction.DIRECT,
                    order = 0,
                ),
            ),
            perApp = listOf(PerAppOutbound.Direct(setOf("com.example.app"))),
            rulePacks = listOf(
                pack("ads-enhanced", RulePackAction.REJECT),
                pack("streaming", RulePackAction.PROXY),
            ),
        )
        val route = json.substringAfter("\"route\":{").substringAfter("\"rules\":[")
        val app = route.indexOf("\"package_name\"")
        val reject = route.indexOf("\"rule_set\":\"pack-ads-enhanced\",\"action\":\"reject\"")
        val proxy = route.indexOf(
            "\"rule_set\":\"pack-streaming\",\"action\":\"route\",\"outbound\":\"proxy\"",
        )
        val user = route.indexOf("\"domain_suffix\":[\"example.com\"]")

        assertTrue(reject >= 0)
        assertTrue(proxy >= 0)
        // Per-app is still the most specific thing the user can say.
        assertTrue("per-app comes first", app < reject)
        // And a rule someone wrote by hand can override a pack.
        assertTrue("user rules come after packs", proxy < user)
        // Both sets are declared, or the rules reference nothing.
        assertTrue(json.contains("\"tag\":\"pack-ads-enhanced\""))
        assertTrue(json.contains("\"tag\":\"pack-streaming\""))
        assertTrue(json.contains("\"path\":\"/files/rulepacks/streaming.srs\""))
    }

    @Test
    fun `a proxy pack names the proxy even when everything else is direct`() {
        val json = builder.build(
            vlessProfile(),
            preset = RoutePreset.GLOBAL_DIRECT,
            rulePacks = listOf(pack("streaming", RulePackAction.PROXY)),
        )

        assertTrue(json.contains("\"final\":\"direct\""))
        // The final is direct, so naming it here would send the pack's domains
        // the wrong way — the opposite of what subscribing means.
        assertTrue(
            json.contains(
                "\"rule_set\":\"pack-streaming\",\"action\":\"route\",\"outbound\":\"proxy\"",
            ),
        )
    }

    private fun pack(id: String, action: RulePackAction) = InstalledRulePack(
        pack = RulePack(
            id = id,
            name = id,
            action = action,
            fileName = "$id.srs",
            sha256 = "",
            urls = emptyList(),
        ),
        path = "/files/rulepacks/$id.srs",
    )

    @Test
    fun `every config carries the Go memory tuning`() {
        val json = builder.build(vlessProfile())

        // GOGC=50 and a 96 MiB process budget (the kernel divides by 1.5 for
        // the Go heap soft limit). Both are `experimental.debug` fields, which
        // the kernel applies at box start.
        assertTrue(
            json.contains("\"debug\":{\"gc_percent\":50,\"memory_limit\":100663296}"),
        )
        // The blocking config is a kernel config too; a Kill Switch must not
        // run with different memory behaviour.
        assertTrue(
            builder.buildBlocking().contains("\"gc_percent\":50"),
        )
    }

    @Test
    fun `the local API is absent by default and loopback-only when asked for`() {
        val off = builder.build(vlessProfile())
        assertTrue("the traffic manager needs the key even when off", off.contains("\"clash_api\":{}"))
        assertFalse(off.contains("external_controller"))

        val on = builder.build(vlessProfile(), localApi = LocalApiConfig("tok-123"))
        assertTrue(on.contains("\"external_controller\":\"127.0.0.1:9090\""))
        assertTrue(on.contains("\"secret\":\"tok-123\""))
        // Never a wildcard address: the listener must not be reachable from
        // the LAN, which is the acceptance criterion this line protects.
        assertFalse(on.contains("\"external_controller\":\"0.0.0.0"))
    }

    @Test
    fun `per-app exceptions are the first rule and name the packages`() {
        val json = builder.build(
            vlessProfile(),
            perApp = listOf(
                PerAppOutbound.Direct(setOf("com.example.broker", "com.example.bank")),
            ),
        )

        // Sorted, so the emitted config is deterministic regardless of the set's
        // iteration order.
        assertTrue(
            json.contains(
                "\"rules\":[{\"package_name\":[\"com.example.bank\",\"com.example.broker\"]," +
                    "\"action\":\"route\",\"outbound\":\"direct\"}",
            ),
        )
    }

    @Test
    fun `a per-app node gets its own outbound and the rule points at it`() {
        // The selected target is `proxy`; an app sent elsewhere needs that node
        // in the config too, under a tag of its own.
        val aiNode = vlessProfile(uuid = "22222222-3333-4444-5555-666666666666").copy(name = "ai")
        val json = builder.build(
            vlessProfile(),
            perApp = listOf(PerAppOutbound.Node(setOf("com.example.ai"), aiNode)),
        )

        assertTrue(json.contains("\"tag\":\"per-app-0\""))
        assertTrue(
            json.contains(
                "\"package_name\":[\"com.example.ai\"],\"action\":\"route\",\"outbound\":\"per-app-0\"",
            ),
        )
        // The default target is untouched.
        assertTrue(json.contains("\"final\":\"proxy\""))
    }

    @Test
    fun `a per-app exception outranks the preset`() {
        // Someone who says "this app goes this way" means it more specifically
        // than any domain rule, so a bank app reaching a preset-matched domain
        // must still take the route it was given.
        val json = builder.build(
            vlessProfile(),
            mainland = mainland, preset = RoutePreset.BYPASS_MAINLAND,
            perApp = listOf(PerAppOutbound.Direct(setOf("com.example.bank"))),
        )
        val route = json.substringAfter("\"route\":{")
        val perApp = route.indexOf("\"package_name\"")
        val geosite = route.indexOf("\"rule_set\":\"geosite-cn\",\"action\":\"route\",\"outbound\":\"direct\"")

        assertTrue(perApp >= 0)
        assertTrue(geosite >= 0)
        assertTrue("per-app comes before the preset", perApp < geosite)
    }

    @Test
    fun `the local network is direct in every preset`() {
        // Infrastructure, not a preference: a private address sent to `final`
        // is a router, NAS or printer that cannot be reached at all.
        for (preset in RoutePreset.entries) {
            val json = builder.build(vlessProfile(), preset = preset)
            for (cidr in ConfigBuilder.LAN_CIDRS) {
                assertTrue("$preset is missing $cidr", json.contains("\"$cidr\""))
            }
            assertTrue(
                "$preset routes them direct",
                json.contains("\"ip_cidr\":[\"10.0.0.0/8\"") &&
                    json.contains("\"outbound\":\"direct\""),
            )
        }
    }

    @Test
    fun `the local-network rule sits after per-app and before the preset`() {
        val json = builder.build(
            vlessProfile(),
            mainland = mainland,
            preset = RoutePreset.BYPASS_MAINLAND,
            perApp = listOf(PerAppOutbound.Direct(setOf("com.example.bank"))),
        )
        val route = json.substringAfter("\"route\":{")
        val perApp = route.indexOf("\"package_name\"")
        val lan = route.indexOf("\"ip_cidr\"")
        val geosite = route.indexOf("\"rule_set\":\"geosite-cn\"")

        assertTrue(perApp >= 0 && lan >= 0 && geosite >= 0)
        assertTrue("per-app before the local network", perApp < lan)
        assertTrue("local network before the preset", lan < geosite)
    }

    @Test
    fun `no per-app selection emits no package rule`() {
        // The rule only works because the platform answers "which app owns
        // this connection"; when nothing is selected there is no reason to ask.
        assertFalse(builder.build(vlessProfile()).contains("package_name"))
        assertFalse(builder.build(listOf(vlessProfile())).contains("package_name"))
    }

    private companion object {
        /**
         * The M4 target, and the CI gate for it.
         *
         * This is the one M4 number a runner without a device can measure, so
         * it is the one that can fail a pull request. The rest — cold start,
         * frame times, resident memory, reconnect, interaction latency — need
         * the `benchmark` module on real hardware.
         *
         * ~4 ms measured, so the budget is 25x the real cost. That is still a
         * gate: a per-node scan over all nodes would take seconds, and even a
         * tenfold regression fails here. It is not tighter because a wall-clock
         * assertion with no headroom is a flake generator — one GC pause or a
         * runner without a spare compiler thread is enough — and a gate that
         * cries wolf gets disabled, which is worse than a loose one.
         */
        const val BUDGET_MS = 100L

        /** Enough for C1/C2 to compile the hot path before anything is timed. */
        const val WARMUP_RUNS = 5
        const val MEASURED_RUNS = 5
    }
}
