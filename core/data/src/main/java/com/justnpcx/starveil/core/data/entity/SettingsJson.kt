package com.justnpcx.starveil.core.data.entity

import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.MuxOptions
import com.justnpcx.starveil.core.model.ObfsOptions
import com.justnpcx.starveil.core.model.ProfileSettings
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
import com.justnpcx.starveil.core.model.json.Json
import com.justnpcx.starveil.core.model.json.JsonException

/**
 * Serialises [ProfileSettings] to/from `ProfileEntity.settingsJson`.
 *
 * This is what makes the import -> persist -> reload round-trip lossless:
 * without it, a subscription-imported node would lose its UUID / password /
 * REALITY keys the moment it was saved.
 *
 * Never logged (the JSON carries credentials). Decoding is tolerant: unknown
 * or missing fields fall back to safe defaults, and a malformed blob yields
 * null rather than crashing — old or hand-edited data must not brick the app.
 */
internal object SettingsJson {

    private const val KEY_KIND = "kind"

    fun encode(settings: ProfileSettings?): String {
        if (settings == null) return "{}"
        val map: Map<String, Any?> = when (settings) {
            is VlessSettings -> linkedMapOf(
                KEY_KIND to "vless",
                "uuid" to settings.uuid,
                "flow" to settings.flow,
                "tls" to encodeTls(settings.tls),
                "transport" to encodeTransport(settings.transport),
                "mux" to encodeMux(settings.mux),
            )

            is TrojanSettings -> linkedMapOf(
                KEY_KIND to "trojan",
                "password" to settings.password,
                "tls" to encodeTls(settings.tls),
                "transport" to encodeTransport(settings.transport),
                "mux" to encodeMux(settings.mux),
            )

            is ShadowsocksSettings -> linkedMapOf(
                KEY_KIND to "shadowsocks",
                "method" to settings.method,
                "password" to settings.password,
            )

            is VmessSettings -> linkedMapOf(
                KEY_KIND to "vmess",
                "uuid" to settings.uuid,
                "security" to settings.security,
                "alterId" to settings.alterId,
                "tls" to encodeTls(settings.tls),
                "transport" to encodeTransport(settings.transport),
                "mux" to encodeMux(settings.mux),
            )

            is Hysteria2Settings -> linkedMapOf(
                KEY_KIND to "hysteria2",
                "password" to settings.password,
                "obfs" to settings.obfs?.let {
                    linkedMapOf<String, Any?>("type" to it.type, "password" to it.password)
                },
                "upMbps" to settings.upMbps,
                "downMbps" to settings.downMbps,
                "serverPorts" to settings.serverPorts.takeIf { it.isNotEmpty() },
                "hopInterval" to settings.hopInterval,
                "tls" to encodeTls(settings.tls),
            )

            is TuicSettings -> linkedMapOf(
                KEY_KIND to "tuic",
                "uuid" to settings.uuid,
                "password" to settings.password,
                "congestionControl" to settings.congestionControl?.wireName,
                "udpRelayMode" to settings.udpRelayMode?.wireName,
                "zeroRttHandshake" to settings.zeroRttHandshake,
                "tls" to encodeTls(settings.tls),
            )

            is WireGuardSettings -> linkedMapOf(
                KEY_KIND to "wireguard",
                "privateKey" to settings.privateKey,
                "address" to settings.address,
                "mtu" to settings.mtu,
                "listenPort" to settings.listenPort,
                "peers" to settings.peers.map { peer ->
                    linkedMapOf<String, Any?>(
                        "publicKey" to peer.publicKey,
                        "endpointAddress" to peer.endpointAddress,
                        "endpointPort" to peer.endpointPort,
                        "allowedIps" to peer.allowedIps.takeIf { it.isNotEmpty() },
                        "preSharedKey" to peer.preSharedKey,
                        "persistentKeepalive" to peer.persistentKeepalive,
                    )
                },
            )
        }
        return Json.write(map)
    }

    fun decode(json: String, type: ProfileType): ProfileSettings? {
        if (json.isBlank()) return null
        val map = try {
            Json.parseObject(json)
        } catch (e: JsonException) {
            return null
        }
        return when (type) {
            ProfileType.VLESS -> {
                val uuid = map.str("uuid") ?: return null
                VlessSettings(
                    uuid = uuid,
                    flow = map.str("flow"),
                    tls = decodeTls(map["tls"]),
                    transport = decodeTransport(map["transport"]),
                    mux = decodeMux(map["mux"]),
                )
            }

            ProfileType.TROJAN -> {
                val password = map.str("password") ?: return null
                TrojanSettings(
                    password = password,
                    tls = decodeTls(map["tls"]),
                    transport = decodeTransport(map["transport"]),
                    mux = decodeMux(map["mux"]),
                )
            }

            ProfileType.SHADOWSOCKS -> {
                val method = map.str("method") ?: return null
                val password = map.str("password") ?: return null
                ShadowsocksSettings(method = method, password = password)
            }

            ProfileType.VMESS -> {
                val uuid = map.str("uuid") ?: return null
                VmessSettings(
                    uuid = uuid,
                    security = map.str("security") ?: "auto",
                    alterId = map.int("alterId") ?: 0,
                    tls = decodeTls(map["tls"]),
                    transport = decodeTransport(map["transport"]),
                    mux = decodeMux(map["mux"]),
                )
            }

            ProfileType.HYSTERIA2 -> {
                val password = map.str("password") ?: return null
                Hysteria2Settings(
                    password = password,
                    // An incomplete obfs block is dropped: sing-box fails the
                    // connection on an obfs without a password.
                    obfs = map["obfs"].asMap()?.let { o ->
                        val type = o.str("type") ?: return@let null
                        val obfsPassword = o.str("password") ?: return@let null
                        ObfsOptions(type = type, password = obfsPassword)
                    },
                    upMbps = map.int("upMbps"),
                    downMbps = map.int("downMbps"),
                    serverPorts = map["serverPorts"].asStringList(),
                    hopInterval = map.str("hopInterval"),
                    tls = decodeTls(map["tls"]),
                )
            }

            ProfileType.TUIC -> {
                val uuid = map.str("uuid") ?: return null
                val password = map.str("password") ?: return null
                TuicSettings(
                    uuid = uuid,
                    password = password,
                    // Unknown values decode to null rather than being carried
                    // forward: sing-box rejects the whole config on an unknown
                    // enum, which would stop the kernel.
                    congestionControl = map.str("congestionControl")
                        ?.let { TuicCongestionControl.fromWireName(it) },
                    udpRelayMode = map.str("udpRelayMode")
                        ?.let { TuicUdpRelayMode.fromWireName(it) },
                    zeroRttHandshake = map.bool("zeroRttHandshake") ?: false,
                    tls = decodeTls(map["tls"]),
                )
            }

            ProfileType.WIREGUARD -> {
                val privateKey = map.str("privateKey") ?: return null
                val address = map["address"].asStringList()
                if (address.isEmpty()) return null
                val peers = (map["peers"] as? List<*>)
                    ?.mapNotNull { it.asMap() }
                    ?.mapNotNull { peer ->
                        val publicKey = peer.str("publicKey") ?: return@mapNotNull null
                        val host = peer.str("endpointAddress") ?: return@mapNotNull null
                        val port = peer.int("endpointPort") ?: return@mapNotNull null
                        WireGuardPeer(
                            publicKey = publicKey,
                            endpointAddress = host,
                            endpointPort = port,
                            allowedIps = peer["allowedIps"].asStringList(),
                            preSharedKey = peer.str("preSharedKey"),
                            persistentKeepalive = peer.int("persistentKeepalive"),
                        )
                    }
                    ?: emptyList()
                if (peers.isEmpty()) return null
                WireGuardSettings(
                    privateKey = privateKey,
                    address = address,
                    peers = peers,
                    mtu = map.int("mtu"),
                    listenPort = map.int("listenPort"),
                )
            }

            // Not modelled yet — nothing to decode.
            else -> null
        }
    }

    private fun encodeTls(tls: TlsOptions): Map<String, Any?> = linkedMapOf(
        "security" to tls.security.name.lowercase(),
        "sni" to tls.sni,
        "alpn" to tls.alpn.takeIf { it.isNotEmpty() },
        "fingerprint" to tls.fingerprint,
        "allowInsecure" to tls.allowInsecure,
        "reality" to tls.reality?.let { r ->
            linkedMapOf(
                "publicKey" to r.publicKey,
                "shortId" to r.shortId,
                "mlKem" to r.mlKem,
            )
        },
    )

    private fun decodeTls(value: Any?): TlsOptions {
        val map = value.asMap() ?: return TlsOptions.None
        val security = when (map.str("security")?.lowercase()) {
            "tls" -> SecurityMode.TLS
            "reality" -> SecurityMode.REALITY
            else -> SecurityMode.NONE
        }
        val reality = map["reality"].asMap()?.let { r ->
            val publicKey = r.str("publicKey") ?: return@let null
            RealityOptions(
                publicKey = publicKey,
                shortId = r.str("shortId"),
                // Profiles stored before the flag existed recorded the value
                // that used to drive this; honour it rather than silently
                // turning the hybrid handshake off for an existing node.
                mlKem = r.bool("mlKem") ?: !r.str("mlKemPublicKey").isNullOrBlank(),
            )
        }
        return TlsOptions(
            security = security,
            sni = map.str("sni"),
            alpn = map["alpn"].asStringList(),
            fingerprint = map.str("fingerprint"),
            allowInsecure = map.bool("allowInsecure") ?: false,
            reality = reality,
        )
    }

    private fun encodeTransport(t: TransportOptions): Map<String, Any?> = linkedMapOf(
        "type" to t.type.wireName,
        "host" to t.host,
        "path" to t.path,
        "serviceName" to t.serviceName,
        "mode" to t.mode?.wireName,
        "headers" to t.headers.takeIf { it.isNotEmpty() },
    )

    private fun decodeTransport(value: Any?): TransportOptions {
        val map = value.asMap() ?: return TransportOptions.Tcp
        val type = when (map.str("type")?.lowercase()) {
            "ws" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "http" -> TransportType.HTTP
            "xhttp" -> TransportType.XHTTP
            else -> TransportType.TCP
        }
        return TransportOptions(
            type = type,
            host = map.str("host"),
            path = map.str("path"),
            serviceName = map.str("serviceName"),
            // An unrecognised mode (an older/newer `packet-up`, say) decodes to
            // null so the core picks its own default rather than being handed a
            // mode it may not implement.
            mode = map.str("mode")?.let { XhttpMode.fromWireName(it) },
            headers = map["headers"].asStringMap(),
        )
    }

    private fun encodeMux(mux: MuxOptions?): Map<String, Any?>? =
        mux?.let { linkedMapOf("enabled" to it.enabled, "protocol" to it.protocol, "padding" to it.padding) }

    private fun decodeMux(value: Any?): MuxOptions? {
        val map = value.asMap() ?: return null
        return MuxOptions(
            enabled = map.bool("enabled") ?: false,
            protocol = map.str("protocol"),
            padding = map.bool("padding"),
        )
    }

    // ----------------------------------------------------- tolerant accessors

    private fun Any?.asMap(): Map<*, *>? = this as? Map<*, *>

    private fun Map<*, *>.str(key: String): String? =
        (this[key] as? String)?.takeIf { it.isNotEmpty() }

    private fun Map<*, *>.bool(key: String): Boolean? = this[key] as? Boolean

    private fun Map<*, *>.int(key: String): Int? = when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    private fun Any?.asStringList(): List<String> =
        (this as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    private fun Any?.asStringMap(): Map<String, String> =
        (this as? Map<*, *>)
            ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
            ?.toMap()
            ?: emptyMap()
}
