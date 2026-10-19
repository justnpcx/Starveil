package com.justnpcx.starveil.core.kernel.protocol

import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.MuxOptions
import com.justnpcx.starveil.core.model.ObfsOptions
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.RealityOptions
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
import com.justnpcx.starveil.core.model.WireGuardPeer
import com.justnpcx.starveil.core.model.WireGuardSettings
import com.justnpcx.starveil.core.model.XhttpMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Export → import → compare, for every protocol.
 *
 * The share link is the app's own format for a node, so the property that
 * matters is not "does it look like a link" but "can this app read back what it
 * wrote". Every profile here uses non-default values throughout: a field the
 * writer drops is invisible against a default, and dropping one silently is
 * exactly the failure this is meant to catch.
 */
class LinkExportRoundTripTest {

    private val registry = ProtocolRegistry.default()

    @Test
    fun `vless survives the round trip`() {
        assertRoundTrip(
            Profile(
                name = "vless node",
                type = ProfileType.VLESS,
                server = "v.example.com",
                port = 8443,
                settings = VlessSettings(
                    uuid = "11111111-2222-3333-4444-555555555555",
                    flow = "xtls-rprx-vision",
                    tls = TlsOptions(
                        security = SecurityMode.REALITY,
                        sni = "www.intel.com",
                        alpn = listOf("h2", "http/1.1"),
                        fingerprint = "chrome",
                        allowInsecure = true,
                        reality = RealityOptions(
                            publicKey = "PBK-VALUE",
                            shortId = "ab12",
                            mlKem = true,
                        ),
                    ),
                    transport = TransportOptions(
                        type = TransportType.XHTTP,
                        host = "cdn.example",
                        path = "/xhttp/",
                        mode = XhttpMode.STREAM_UP,
                    ),
                    mux = MuxOptions(enabled = true, protocol = "smux", padding = true),
                ),
            ),
        )
    }

    @Test
    fun `trojan survives the round trip`() {
        assertRoundTrip(
            Profile(
                name = "trojan node",
                type = ProfileType.TROJAN,
                server = "t.example.com",
                port = 443,
                settings = TrojanSettings(
                    // A password with the characters that break a query string.
                    password = "p@ss:w/rd?#&=+",
                    tls = TlsOptions(
                        security = SecurityMode.TLS,
                        sni = "t.example.com",
                        fingerprint = "firefox",
                    ),
                    transport = TransportOptions(type = TransportType.WS, path = "/ws", host = "cdn"),
                ),
            ),
        )
    }

    @Test
    fun `shadowsocks survives the round trip`() {
        assertRoundTrip(
            Profile(
                name = "ss node",
                type = ProfileType.SHADOWSOCKS,
                server = "s.example.com",
                port = 8388,
                settings = ShadowsocksSettings(
                    method = "chacha20-ietf-poly1305",
                    password = "a:b/c@d",
                ),
            ),
        )
    }

    @Test
    fun `vmess survives the round trip`() {
        assertRoundTrip(
            Profile(
                name = "vmess node",
                type = ProfileType.VMESS,
                server = "m.example.com",
                port = 443,
                settings = VmessSettings(
                    uuid = "99999999-8888-7777-6666-555555555555",
                    security = "aes-128-gcm",
                    alterId = 64,
                    tls = TlsOptions(
                        security = SecurityMode.TLS,
                        sni = "m.example.com",
                        alpn = listOf("h2"),
                        fingerprint = "chrome",
                    ),
                    transport = TransportOptions(type = TransportType.WS, path = "/vm", host = "cdn"),
                ),
            ),
        )
    }

    @Test
    fun `hysteria2 survives the round trip`() {
        assertRoundTrip(
            Profile(
                name = "hy2 node",
                type = ProfileType.HYSTERIA2,
                server = "h.example.com",
                port = 8443,
                settings = Hysteria2Settings(
                    password = "hunter2",
                    obfs = ObfsOptions(type = "salamander", password = "obfs-pass"),
                    upMbps = 100,
                    downMbps = 200,
                    tls = TlsOptions(security = SecurityMode.TLS, sni = "h.example.com"),
                    serverPorts = listOf("20000:30000", "40000:41000"),
                    hopInterval = "15s",
                ),
            ),
        )
    }

    @Test
    fun `tuic survives the round trip`() {
        assertRoundTrip(
            Profile(
                name = "tuic node",
                type = ProfileType.TUIC,
                server = "u.example.com",
                port = 443,
                settings = TuicSettings(
                    uuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                    password = "tuic-pass",
                    congestionControl = TuicCongestionControl.BBR,
                    udpRelayMode = TuicUdpRelayMode.QUIC,
                    zeroRttHandshake = true,
                    tls = TlsOptions(security = SecurityMode.TLS, sni = "u.example.com"),
                ),
            ),
        )
    }

    @Test
    fun `wireguard survives the round trip`() {
        // WireGuard has no URI, so the "link" is the .conf and it comes back
        // through the sniffer. Its name is derived by the parser — a .conf has
        // nowhere to put one — so the name is the one field not compared.
        val profile = Profile(
            name = "wg node",
            type = ProfileType.WIREGUARD,
            server = "wg.example.com",
            port = 51820,
            settings = WireGuardSettings(
                privateKey = "PRIVATE-KEY-BASE64=",
                address = listOf("10.0.0.2/32", "fd00::2/128"),
                peers = listOf(
                    WireGuardPeer(
                        publicKey = "PEER-PUBLIC-KEY=",
                        endpointAddress = "wg.example.com",
                        endpointPort = 51820,
                        allowedIps = listOf("0.0.0.0/0", "::/0"),
                        preSharedKey = "PSK=",
                        persistentKeepalive = 25,
                    ),
                ),
                mtu = 1420,
                listenPort = 51821,
            ),
        )

        val link = registry.writeLink(profile)
        assertNotNull("no link was produced for WireGuard", link)

        val parsed = registry.all().firstNotNullOfOrNull { it.linkParser(link!!) }
        assertNotNull("the WireGuard link did not parse back", parsed)
        assertEquals(profile.settings, parsed!!.settings)
        assertEquals(profile.server, parsed.server)
        assertEquals(profile.port, parsed.port)
    }

    @Test
    fun `a profile no protocol can express produces no link`() {
        val other = Profile(name = "n", type = ProfileType.OTHER, server = "x", port = 1)
        assertNull(registry.writeLink(other))
    }

    @Test
    fun `a vless node with a transport host but no sni keeps having none`() {
        // The parser falls back to the transport `host` when a link says nothing
        // about the SNI, so a writer that simply omits it would hand back a node
        // with an SNI it never had.
        assertRoundTrip(
            Profile(
                name = "no sni",
                type = ProfileType.VLESS,
                server = "n.example.com",
                port = 443,
                settings = VlessSettings(
                    uuid = "11111111-2222-3333-4444-555555555555",
                    tls = TlsOptions(security = SecurityMode.TLS),
                    transport = TransportOptions(type = TransportType.WS, host = "cdn.example", path = "/w"),
                ),
            ),
        )
    }

    private fun assertRoundTrip(profile: Profile) {
        val link = registry.writeLink(profile)
        assertNotNull("no link was produced for ${profile.type}", link)
        assertTrue("the link is not a ${profile.type} link: $link", link!!.isNotBlank())

        // Parsed the way the app imports: whatever claims the text.
        val parsed = registry.all().firstNotNullOfOrNull { it.linkParser(link) }
        assertNotNull("the exported link did not parse back: $link", parsed)

        assertEquals("type", profile.type, parsed!!.type)
        assertEquals("name", profile.name, parsed.name)
        assertEquals("server", profile.server, parsed.server)
        assertEquals("port", profile.port, parsed.port)
        assertEquals("settings", profile.settings, parsed.settings)
    }
}
