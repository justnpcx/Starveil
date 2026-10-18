package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.SecurityMode
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import com.justnpcx.starveil.core.model.TlsOptions
import com.justnpcx.starveil.core.model.TransportOptions
import com.justnpcx.starveil.core.model.TransportType
import com.justnpcx.starveil.core.model.TrojanSettings
import com.justnpcx.starveil.core.model.VlessSettings
import com.justnpcx.starveil.core.model.json.Json

/**
 * sing-box's own config, used as a subscription.
 *
 * Some providers hand out a full `config.json` instead of a link list; the
 * outbounds in it are exactly what this app builds at connect time, so they
 * map onto profiles almost field for field.
 *
 * Scope, stated rather than implied: `vless`, `trojan`, `shadowsocks` and
 * `hysteria2`, with TLS and the transports this app models. Anything else —
 * a different protocol, a REALITY block, an unknown transport — is **dropped**
 * rather than imported with fields missing. A profile that cannot connect is
 * worse than an absent one: the user sees a node, tries it, and gets a kernel
 * error that says nothing about the subscription.
 */
class SingBoxJsonSubscriptionParser : SubscriptionParser {

    override fun parse(text: String): List<Profile> {
        val root = runCatching { Json.parseObject(text) }.getOrNull() ?: return emptyList()
        val outbounds = root["outbounds"] as? List<*> ?: return emptyList()
        return outbounds.mapNotNull { entry -> (entry as? Map<*, *>)?.toProfile() }
    }

    private fun Map<*, *>.toProfile(): Profile? {
        val type = this["type"] as? String ?: return null
        val server = (this["server"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val port = (this["server_port"] as? Number)?.toInt()?.takeIf { it in 1..65535 } ?: return null
        val name = (this["tag"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: "$server:$port"
        val tls = tlsOptions() ?: return null
        val transport = transportOptions()

        val profileType = when (type) {
            "vless" -> ProfileType.VLESS
            "trojan" -> ProfileType.TROJAN
            "shadowsocks" -> ProfileType.SHADOWSOCKS
            "hysteria2" -> ProfileType.HYSTERIA2
            else -> return null
        }

        val settings = when (type) {
            "vless" -> {
                val uuid = (this["uuid"] as? String)?.takeIf { it.isNotBlank() } ?: return null
                VlessSettings(
                    uuid = uuid,
                    flow = (this["flow"] as? String)?.takeIf { it.isNotBlank() },
                    tls = tls,
                    transport = transport,
                )
            }

            "trojan" -> {
                val password = (this["password"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
                TrojanSettings(password = password, tls = tls, transport = transport)
            }

            "shadowsocks" -> {
                val method = (this["method"] as? String)?.takeIf { it.isNotBlank() } ?: return null
                val password = (this["password"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
                ShadowsocksSettings(method = method, password = password)
            }

            else -> {
                val password = (this["password"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
                Hysteria2Settings(password = password, tls = tls)
            }
        }

        return Profile(
            name = name,
            type = profileType,
            server = server,
            port = port,
            settings = settings,
        )
    }

    /**
     * `{"enabled":true,"server_name":"…","insecure":true,"alpn":[…],
     *   "utls":{"enabled":true,"fingerprint":"chrome"}}`
     *
     * REALITY is not mapped: the model's [TlsOptions.reality] needs the public
     * key and short id, and a half-mapped REALITY block connects to nothing.
     * An outbound with one is dropped by [toProfile] because its TLS block
     * comes back as `null`.
     */
    private fun Map<*, *>.tlsOptions(): TlsOptions? {
        val tls = this["tls"] as? Map<*, *> ?: return TlsOptions.None
        if (tls["enabled"] != true) return TlsOptions.None
        if (tls["reality"] != null) return null
        val utls = tls["utls"] as? Map<*, *>
        return TlsOptions(
            security = SecurityMode.TLS,
            sni = (tls["server_name"] as? String)?.takeIf { it.isNotBlank() },
            alpn = (tls["alpn"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            fingerprint = (utls?.get("fingerprint") as? String)?.takeIf { it.isNotBlank() },
            allowInsecure = tls["insecure"] == true,
        )
    }

    private fun Map<*, *>.transportOptions(): TransportOptions {
        val transport = this["transport"] as? Map<*, *> ?: return TransportOptions.Tcp
        val type = when (transport["type"] as? String) {
            "ws" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "http" -> TransportType.HTTP
            "xhttp" -> TransportType.XHTTP
            else -> return TransportOptions.Tcp
        }
        val headers = (transport["headers"] as? Map<*, *>)
            ?.entries
            ?.mapNotNull { (key, value) ->
                if (key is String && value is String) key to value else null
            }
            ?.toMap()
            .orEmpty()
        return TransportOptions(
            type = type,
            host = headers["Host"] ?: headers["host"],
            path = (transport["path"] as? String)?.takeIf { it.isNotBlank() },
            serviceName = (transport["service_name"] as? String)?.takeIf { it.isNotBlank() },
            headers = headers,
        )
    }

    companion object {
        /** An object with an `outbounds` array, and not a SIP008 `servers` one. */
        fun sniff(text: String): Boolean {
            val trimmed = text.trimStart()
            if (!trimmed.startsWith("{")) return false
            return runCatching {
                val root = Json.parseObject(trimmed)
                root["outbounds"] is List<*> && root["servers"] == null
            }.getOrDefault(false)
        }
    }
}
