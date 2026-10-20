package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.MuxOptions
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.RealityOptions
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import com.justnpcx.starveil.core.model.TlsOptions
import com.justnpcx.starveil.core.model.TransportOptions
import com.justnpcx.starveil.core.model.TransportType
import com.justnpcx.starveil.core.model.TrojanSettings
import com.justnpcx.starveil.core.model.TuicSettings
import com.justnpcx.starveil.core.model.VlessSettings
import com.justnpcx.starveil.core.model.VmessSettings
import com.justnpcx.starveil.core.model.WireGuardSettings

/**
 * Pure `Profile + settings -> sing-box outbound fragment` builders.
 *
 * No IO, no logging, no credential capture: the returned map is the only
 * place credentials live, and callers must never log it (see README
 * "安全红线"). Field names follow sing-box 1.14 (`server`, `server_port`,
 * `tls`, `transport`, `multiplex`).
 */
internal object SingBoxOutbounds {

    fun vless(profile: Profile, s: VlessSettings): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "type" to "vless",
            "tag" to profile.name,
            "server" to profile.server,
            "server_port" to profile.port,
            "uuid" to s.uuid,
            // The encoding current VLESS servers expect for UDP. Without it
            // QUIC falls back to the legacy path, which some servers drop.
            "packet_encoding" to "xudp",
        ).apply {
            s.flow?.takeIf { it.isNotBlank() }?.let { put("flow", it) }
            tlsBlock(s.tls)?.let { put("tls", it) }
            transportBlock(s.transport)?.let { put("transport", it) }
            muxBlock(s.mux)?.let { put("multiplex", it) }
        }

    fun trojan(profile: Profile, s: TrojanSettings): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "type" to "trojan",
            "tag" to profile.name,
            "server" to profile.server,
            "server_port" to profile.port,
            "password" to s.password,
        ).apply {
            tlsBlock(s.tls)?.let { put("tls", it) }
            transportBlock(s.transport)?.let { put("transport", it) }
            muxBlock(s.mux)?.let { put("multiplex", it) }
        }

    fun vmess(profile: Profile, s: VmessSettings): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "type" to "vmess",
            "tag" to profile.name,
            "server" to profile.server,
            "server_port" to profile.port,
            "uuid" to s.uuid,
            "security" to s.security,
            "alter_id" to s.alterId,
        ).apply {
            tlsBlock(s.tls)?.let { put("tls", it) }
            transportBlock(s.transport)?.let { put("transport", it) }
            muxBlock(s.mux)?.let { put("multiplex", it) }
        }

    fun shadowsocks(profile: Profile, s: ShadowsocksSettings): Map<String, Any?> =
        linkedMapOf(
            "type" to "shadowsocks",
            "tag" to profile.name,
            "server" to profile.server,
            "server_port" to profile.port,
            "method" to s.method,
            "password" to s.password,
        )

    /**
     * Hysteria2 is QUIC-based: no stream transport and no multiplex, and the
     * TLS block is where its SNI / ALPN / insecure live. `obfs` is only
     * emitted when it is complete — a half-filled obfs block fails at connect.
     */
    fun hysteria2(profile: Profile, s: Hysteria2Settings): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "type" to "hysteria2",
            "tag" to profile.name,
            "server" to profile.server,
            "server_port" to profile.port,
            "password" to s.password,
        ).apply {
            s.upMbps?.let { put("up_mbps", it) }
            s.downMbps?.let { put("down_mbps", it) }
            // 1.14 turns ChromeParrot on unless told otherwise: the QUIC
            // client imitates Chrome's transport behaviour. On the same node
            // (HY2, 100 MB download) that cost 12.9 MB/s single-stream and
            // 26.5 MB/s across four streams; with it off the same build does
            // 20-25 MB/s and ~36 MB/s. NekoBox's 1.12 core has no such
            // feature, which is why it was faster on this node.
            //
            // The trade-off is camouflage: the parrot exists to look like
            // Chrome's QUIC on the wire. Speed is the priority here; revisit
            // if this node ever needs to survive active probing.
            put("disable_chrome_parrot", true)
            // A hopping node usually refuses the advertised server_port, so
            // the range is not an optimisation — without it the node is dead.
            if (s.serverPorts.isNotEmpty()) {
                put("server_ports", s.serverPorts)
                s.hopInterval?.let { put("hop_interval", it) }
            }
            s.obfs?.let { obfs ->
                put(
                    "obfs",
                    linkedMapOf<String, Any?>("type" to obfs.type, "password" to obfs.password),
                )
            }
            tlsBlock(s.tls)?.let { put("tls", it) }
        }

    /**
     * TUIC is QUIC-based too: no stream transport, no multiplex, and the TLS
     * block carries its SNI / ALPN / insecure. `congestion_control` and
     * `udp_relay_mode` are enums upstream, so they are enums here — an
     * unknown value would fail the whole config, not just this outbound.
     */
    fun tuic(profile: Profile, s: TuicSettings): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "type" to "tuic",
            "tag" to profile.name,
            "server" to profile.server,
            "server_port" to profile.port,
            "uuid" to s.uuid,
            "password" to s.password,
        ).apply {
            s.congestionControl?.let { put("congestion_control", it.wireName) }
            s.udpRelayMode?.let { put("udp_relay_mode", it.wireName) }
            if (s.zeroRttHandshake) put("zero_rtt_handshake", true)
            tlsBlock(s.tls)?.let { put("tls", it) }
        }

    /**
     * A WireGuard **endpoint** fragment, not an outbound.
     *
     * sing-box 1.13 removed the WireGuard outbound ("use WireGuard endpoint
     * instead"), so this map belongs in the config's top-level `endpoints`
     * array. [ConfigBuilder] places it there by asking the descriptor's
     * placement.
     */
    fun wireguard(profile: Profile, s: WireGuardSettings): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "type" to "wireguard",
            "tag" to profile.name,
            "address" to s.address,
            "private_key" to s.privateKey,
        ).apply {
            s.mtu?.let { put("mtu", it) }
            s.listenPort?.let { put("listen_port", it) }
            put(
                "peers",
                s.peers.map { peer ->
                    linkedMapOf<String, Any?>(
                        "address" to peer.endpointAddress,
                        "port" to peer.endpointPort,
                        "public_key" to peer.publicKey,
                    ).apply {
                        peer.preSharedKey?.let { put("pre_shared_key", it) }
                        if (peer.allowedIps.isNotEmpty()) put("allowed_ips", peer.allowedIps)
                        peer.persistentKeepalive?.let { put("persistent_keepalive_interval", it) }
                    }
                },
            )
        }

    /** Returns null when TLS is off, so no empty `tls` block is emitted. */
    fun tlsBlock(tls: TlsOptions): Map<String, Any?>? {
        if (!tls.enabled) return null
        val block = linkedMapOf<String, Any?>("enabled" to true)
        tls.sni?.takeIf { it.isNotBlank() }?.let { block["server_name"] = it }
        if (tls.allowInsecure) block["insecure"] = true
        if (tls.alpn.isNotEmpty()) block["alpn"] = tls.alpn
        tls.fingerprint?.takeIf { it.isNotBlank() }?.let { fp ->
            block["utls"] = linkedMapOf("enabled" to true, "fingerprint" to fp)
        }
        if (tls.security == com.justnpcx.starveil.core.model.SecurityMode.REALITY) {
            tls.reality?.let { block["reality"] = realityBlock(it) }
        }
        return block
    }

    private fun realityBlock(reality: RealityOptions): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "enabled" to true,
            "public_key" to reality.publicKey,
        ).apply {
            reality.shortId?.takeIf { it.isNotBlank() }?.let { put("short_id", it) }
            // sing-box has no field for an ML-KEM key — only this switch,
            // which advertises the X25519MLKEM768 key share.
            //
            // Do NOT be tempted to emit a key: option.Options decodes with
            // DisallowUnknownFields, so an unknown field here fails the whole
            // config and the kernel refuses to start.
            if (reality.mlKem) put("support_x25519mlkem768", true)
        }

    /** Returns null for plain TCP, which sing-box expresses by omission. */
    fun transportBlock(transport: TransportOptions): Map<String, Any?>? {
        if (transport.type == TransportType.TCP) return null
        val block = linkedMapOf<String, Any?>("type" to transport.type.wireName)
        when (transport.type) {
            TransportType.GRPC ->
                transport.serviceName?.takeIf { it.isNotBlank() }?.let { block["service_name"] = it }

            TransportType.HTTP -> {
                transport.host?.takeIf { it.isNotBlank() }?.let { block["host"] = listOf(it) }
                transport.path?.takeIf { it.isNotBlank() }?.let { block["path"] = it }
            }

            // ws / xhttp: host goes into the Host header. xhttp additionally
            // carries how the two directions are split; omitting `mode` leaves
            // the core to pick, which is what an unset mode means.
            else -> {
                transport.path?.takeIf { it.isNotBlank() }?.let { block["path"] = it }
                if (transport.type == TransportType.XHTTP) {
                    transport.mode?.let { block["mode"] = it.wireName }
                    // Only the keys sing-box 1.14 decodes: an unknown one here
                    // fails the whole config, because option.Options reads with
                    // DisallowUnknownFields.
                    transport.uplinkHttpMethod?.takeIf { it.isNotBlank() }
                        ?.let { block["uplink_http_method"] = it }
                    if (transport.noGrpcHeader) block["no_grpc_header"] = true
                    transport.xPaddingBytes?.takeIf { it.isNotBlank() }
                        ?.let { block["x_padding_bytes"] = it }
                    transport.xPaddingKey?.takeIf { it.isNotBlank() }
                        ?.let { block["x_padding_key"] = it }
                    transport.xPaddingHeader?.takeIf { it.isNotBlank() }
                        ?.let { block["x_padding_header"] = it }
                    transport.downloadSettings?.let { downlink ->
                        val settings = linkedMapOf<String, Any?>()
                        downlink.server?.takeIf { it.isNotBlank() }
                            ?.let { settings["server"] = it }
                        downlink.port?.let { settings["port"] = it }
                        downlink.host?.takeIf { it.isNotBlank() }?.let { settings["host"] = it }
                        downlink.path?.takeIf { it.isNotBlank() }?.let { settings["path"] = it }
                        if (settings.isNotEmpty()) block["download_settings"] = settings
                    }
                }
                // `host` is the Host header. Merged rather than substituted,
                // so a node that carries both keeps the other headers too.
                val headerBlock = linkedMapOf<String, String>()
                headerBlock.putAll(transport.headers)
                transport.host?.takeIf { it.isNotBlank() }?.let { host ->
                    if (headerBlock.keys.none { it.equals("Host", ignoreCase = true) }) {
                        headerBlock["Host"] = host
                    }
                }
                if (headerBlock.isNotEmpty()) block["headers"] = headerBlock
            }
        }
        return block
    }

    /** Returns null when mux is absent or disabled. */
    fun muxBlock(mux: MuxOptions?): Map<String, Any?>? {
        if (mux == null || !mux.enabled) return null
        return linkedMapOf<String, Any?>("enabled" to true).apply {
            mux.protocol?.takeIf { it.isNotBlank() }?.let { put("protocol", it) }
            mux.padding?.let { put("padding", it) }
        }
    }
}
