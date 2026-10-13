package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.model.MuxOptions
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.RealityOptions
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import com.justnpcx.starveil.core.model.TlsOptions
import com.justnpcx.starveil.core.model.TransportOptions
import com.justnpcx.starveil.core.model.TransportType
import com.justnpcx.starveil.core.model.TrojanSettings
import com.justnpcx.starveil.core.model.VlessSettings

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

    fun shadowsocks(profile: Profile, s: ShadowsocksSettings): Map<String, Any?> =
        linkedMapOf(
            "type" to "shadowsocks",
            "tag" to profile.name,
            "server" to profile.server,
            "server_port" to profile.port,
            "method" to s.method,
            "password" to s.password,
        )

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
            // Fork extension (ML-KEM). Only emitted when the server advertises
            // it, so upstream sing-box configs are unaffected.
            reality.mlKemPublicKey?.takeIf { it.isNotBlank() }?.let { put("ml_kem_public_key", it) }
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

            // ws / xhttp: host goes into the Host header.
            else -> {
                transport.path?.takeIf { it.isNotBlank() }?.let { block["path"] = it }
                if (transport.headers.isNotEmpty()) {
                    block["headers"] = transport.headers.toMap()
                } else {
                    transport.host?.takeIf { it.isNotBlank() }?.let { host ->
                        block["headers"] = mapOf("Host" to host)
                    }
                }
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
