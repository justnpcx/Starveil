package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.RealityOptions
import com.justnpcx.starveil.core.model.SecurityMode
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import com.justnpcx.starveil.core.model.TlsOptions
import com.justnpcx.starveil.core.model.TransportOptions
import com.justnpcx.starveil.core.model.TransportType
import com.justnpcx.starveil.core.model.TrojanSettings
import com.justnpcx.starveil.core.model.VlessSettings
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
        assertTrue(json.contains("\"reality\":{\"enabled\":true,\"public_key\":\"PUBKEY\",\"short_id\":\"ab12\",\"ml_kem_public_key\":\"MLKEMKEY\"}"))
        assertTrue(json.contains("\"transport\":{\"type\":\"xhttp\",\"path\":\"/x\"}"))
        assertTrue(json.contains("\"multiplex\":{\"enabled\":true,\"protocol\":\"smux\"}"))
        assertTrue(json.contains("\"type\":\"direct\""))
        assertTrue(json.contains("\"type\":\"block\""))
        assertTrue(json.contains("\"final\":\"proxy\""))
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
