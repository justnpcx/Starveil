package com.justnpcx.starveil.feature.editor

import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.SecurityMode
import com.justnpcx.starveil.core.model.TransportType
import com.justnpcx.starveil.core.model.TuicCongestionControl
import com.justnpcx.starveil.core.model.TuicUdpRelayMode
import com.justnpcx.starveil.core.model.XhttpMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor's contract: whatever the user types has to survive a trip through
 * the model, the database, and back into the form — otherwise editing a node
 * silently rewrites it.
 */
class EditorFormTest {

    /**
     * Every field carries a NON-DEFAULT value on purpose. With defaults, a
     * round-trip that silently drops a field still produces an equal profile
     * and the test passes — which is exactly how a broken refill would slip
     * through. (Learned by breaking the refill and watching this pass.)
     */
    private fun validForm(type: ProfileType) = EditorForm(
        type = type,
        name = "node",
        server = "h.example",
        port = "8443",
        security = SecurityMode.TLS,
        sni = "h.example",
        fingerprint = "chrome",
        allowInsecure = true,
        transport = TransportType.WS,
        transportPath = "/ws",
        transportHost = "cdn.example",
        transportServiceName = "svc",
        uuid = "11111111-2222-3333-4444-555555555555",
        password = "pw",
        flow = "xtls-rprx-vision",
        alterId = "64",
        vmessSecurity = "aes-128-gcm",
        ssMethod = "chacha20-ietf-poly1305",
        obfsType = "salamander",
        obfsPassword = "opw",
        upMbps = "100",
        downMbps = "200",
        tuicCongestion = TuicCongestionControl.BBR,
        tuicUdpRelay = TuicUdpRelayMode.NATIVE,
        wgPrivateKey = "priv",
        wgAddress = "10.0.0.2/32",
        wgPeerPublicKey = "pub",
        wgPeerEndpoint = "wg.example",
        wgPeerPort = "51820",
        wgAllowedIps = "0.0.0.0/0",
        wgMtu = "1420",
    )

    private val allTypes = listOf(
        ProfileType.VLESS,
        ProfileType.VMESS,
        ProfileType.TROJAN,
        ProfileType.SHADOWSOCKS,
        ProfileType.HYSTERIA2,
        ProfileType.TUIC,
        ProfileType.WIREGUARD,
    )

    /**
     * The editor's own round trip: what you type becomes a profile, and
     * reopening that profile refills the form with the same thing.
     *
     * The database leg is a separate concern and is already covered for all
     * seven protocols by `ProfileRoundTripTest` in :core:data, which drives the
     * real settings codec through a fake DAO.
     */
    @Test
    fun `every protocol refills the form it was built from`() {
        for (type in allTypes) {
            val form = validForm(type)
            val profile = form.toProfile()
            assertNotNull("$type produced no profile", profile)
            profile!!

            val refilled = EditorForm.from(profile)

            assertEquals("$type did not round-trip through the form", profile, refilled.toProfile())
        }
    }

    @Test
    fun `vless with xhttp and reality keeps the transport and the host`() {
        val form = validForm(ProfileType.VLESS).copy(
            security = SecurityMode.REALITY,
            realityPublicKey = "PBK",
            realityShortId = "ab12",
            realityMlKem = true,
            transport = TransportType.XHTTP,
            transportPath = "/x",
            transportHost = "cdn.example",
            transportMode = XhttpMode.STREAM_UP,
        )

        val settings = form.toProfile()!!.settings as com.justnpcx.starveil.core.model.VlessSettings

        assertEquals(TransportType.XHTTP, settings.transport.type)
        assertEquals("/x", settings.transport.path)
        // xhttp has no `host` field in sing-box; the host travels in the header,
        // which is the emitter's job — the form's job is to keep it at all.
        assertEquals("cdn.example", settings.transport.host)
        assertEquals(XhttpMode.STREAM_UP, settings.transport.mode)
        assertEquals(SecurityMode.REALITY, settings.tls.security)
        assertTrue(settings.tls.reality!!.mlKem)
    }

    @Test
    fun `an empty server is rejected`() {
        val errors = validForm(ProfileType.VLESS).copy(server = "  ").validate()
        assertEquals(EditorError.REQUIRED, errors[EditorField.SERVER])
        assertNull(validForm(ProfileType.VLESS).copy(server = "  ").toProfile())
    }

    @Test
    fun `a port outside the allowed range is rejected`() {
        for (bad in listOf("0", "65536", "70000", "-1", "", "abc")) {
            val errors = validForm(ProfileType.VLESS).copy(port = bad).validate()
            assertEquals("port '$bad' should be rejected", EditorError.INVALID_NUMBER, errors[EditorField.PORT])
        }
        // The boundaries themselves are fine.
        assertTrue(validForm(ProfileType.VLESS).copy(port = "1").validate().isEmpty())
        assertTrue(validForm(ProfileType.VLESS).copy(port = "65535").validate().isEmpty())
    }

    @Test
    fun `reality without a public key is rejected`() {
        val form = validForm(ProfileType.VLESS).copy(
            security = SecurityMode.REALITY,
            realityPublicKey = "",
        )
        assertEquals(EditorError.REQUIRED, form.validate()[EditorField.REALITY_PUBLIC_KEY])
        assertNull(form.toProfile())
    }

    @Test
    fun `protocol credentials are required where they matter`() {
        assertNotNull(validForm(ProfileType.VLESS).copy(uuid = "").validate()[EditorField.UUID])
        assertNotNull(validForm(ProfileType.VMESS).copy(uuid = "").validate()[EditorField.UUID])
        assertNotNull(validForm(ProfileType.TROJAN).copy(password = "").validate()[EditorField.PASSWORD])
        assertNotNull(validForm(ProfileType.SHADOWSOCKS).copy(password = "").validate()[EditorField.PASSWORD])
        assertNotNull(validForm(ProfileType.SHADOWSOCKS).copy(ssMethod = "").validate()[EditorField.METHOD])
        assertNotNull(validForm(ProfileType.HYSTERIA2).copy(password = "").validate()[EditorField.PASSWORD])
        assertNotNull(validForm(ProfileType.TUIC).copy(uuid = "").validate()[EditorField.UUID])
        assertNotNull(validForm(ProfileType.TUIC).copy(password = "").validate()[EditorField.PASSWORD])

        // WireGuard's endpoint is part of its identity, not optional dressing.
        assertNotNull(validForm(ProfileType.WIREGUARD).copy(wgPrivateKey = "").validate()[EditorField.WG_PRIVATE_KEY])
        assertNotNull(validForm(ProfileType.WIREGUARD).copy(wgPeerPublicKey = "").validate()[EditorField.WG_PEER_PUBLIC_KEY])
        assertNotNull(validForm(ProfileType.WIREGUARD).copy(wgPeerEndpoint = "").validate()[EditorField.WG_PEER_ENDPOINT])
        assertNotNull(validForm(ProfileType.WIREGUARD).copy(wgPeerPort = "0").validate()[EditorField.WG_PEER_PORT])
    }

    @Test
    fun `editing keeps the id, group and latency`() {
        val existing = validForm(ProfileType.VLESS).copy(
            id = 42L,
            groupId = 7L,
            latencyMs = 165L,
            frontNodeKey = "VLESS|front.example|443|front",
        ).toProfile()!!

        val refilled = EditorForm.from(existing)

        assertEquals(42L, refilled.id)
        assertEquals(7L, refilled.groupId)
        assertEquals(165L, refilled.latencyMs)
        assertEquals("VLESS|front.example|443|front", refilled.frontNodeKey)
        assertEquals(existing, refilled.toProfile())
    }

    @Test
    fun `a blank name falls back to the server`() {
        val profile = validForm(ProfileType.VLESS).copy(name = "   ").toProfile()!!
        assertEquals("h.example", profile.name)
    }

    @Test
    fun `quic protocols never end up without tls`() {
        // Hysteria2 and TUIC are QUIC: TLS is not optional, so a form that has
        // not chosen a security mode must still build one.
        for (type in listOf(ProfileType.HYSTERIA2, ProfileType.TUIC)) {
            val form = validForm(type).copy(security = SecurityMode.NONE)
            assertTrue("$type should not allow no security", !form.allowsNoSecurity)
            assertEquals(
                SecurityMode.TLS,
                EditorForm.from(form.toProfile()!!).security,
            )
        }
    }

    @Test
    fun `hysteria2 keeps its bandwidth hints and obfs`() {
        val form = validForm(ProfileType.HYSTERIA2).copy(
            obfsType = "salamander",
            obfsPassword = "opw",
            upMbps = "100",
            downMbps = "200",
        )

        val settings = form.toProfile()!!.settings as com.justnpcx.starveil.core.model.Hysteria2Settings

        assertEquals("salamander", settings.obfs?.type)
        assertEquals("opw", settings.obfs?.password)
        assertEquals(100, settings.upMbps)
        assertEquals(200, settings.downMbps)
    }

    @Test
    fun `a half-filled obfs block is dropped rather than sent broken`() {
        // Both obfs types require a password; emitting the type without it
        // fails at connect, which is worse than not emitting it.
        val settings = validForm(ProfileType.HYSTERIA2)
            .copy(obfsType = "salamander", obfsPassword = "")
            .toProfile()!!.settings as com.justnpcx.starveil.core.model.Hysteria2Settings

        assertNull(settings.obfs)
    }

    @Test
    fun `tuic keeps its two enums`() {
        val form = validForm(ProfileType.TUIC).copy(
            tuicCongestion = TuicCongestionControl.BBR,
            tuicUdpRelay = TuicUdpRelayMode.NATIVE,
        )

        val settings = form.toProfile()!!.settings as com.justnpcx.starveil.core.model.TuicSettings

        assertEquals(TuicCongestionControl.BBR, settings.congestionControl)
        assertEquals(TuicUdpRelayMode.NATIVE, settings.udpRelayMode)
        assertEquals(settings, EditorForm.from(form.toProfile()!!).toProfile()!!.settings)
    }

    @Test
    fun `wireguard splits its comma separated lists`() {
        val form = validForm(ProfileType.WIREGUARD).copy(
            wgAddress = "10.0.0.2/32, fd00::2/128",
            wgAllowedIps = "0.0.0.0/0, ::/0",
        )

        val settings = form.toProfile()!!.settings as com.justnpcx.starveil.core.model.WireGuardSettings

        assertEquals(listOf("10.0.0.2/32", "fd00::2/128"), settings.address)
        assertEquals(listOf("0.0.0.0/0", "::/0"), settings.peers.single().allowedIps)
    }
}
