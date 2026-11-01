package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.EchOptions
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
import com.justnpcx.starveil.core.model.json.Json
import com.justnpcx.starveil.core.model.json.JsonException

/**
 * sing-box's own config, used as a subscription.
 *
 * Some providers hand out a full `config.json` instead of a link list; the
 * outbounds in it are exactly what this app builds at connect time, so they
 * map onto profiles almost field for field.
 *
 * Scope, stated rather than implied: `vless`, `trojan`, `shadowsocks` and
 * `hysteria2`, with TLS (including REALITY, when the block carries its public
 * key) and the transports this app models. Anything else — a different
 * protocol, a REALITY block without a key, an unknown transport — is
 * **dropped** rather than imported with fields missing. A profile that cannot connect is
 * worse than an absent one: the user sees a node, tries it, and gets a kernel
 * error that says nothing about the subscription.
 */
class SingBoxJsonSubscriptionParser : SubscriptionParser {

    override fun parse(text: String): List<Profile> = try {
        parseDetailed(text).profiles
    } catch (e: MalformedSubscriptionException) {
        emptyList()
    }

    override fun parseDetailed(text: String): ParseOutcome {
        val root = try {
            Json.parseObject(text)
        } catch (e: JsonException) {
            throw MalformedSubscriptionException(
                format = DocumentFormat.JSON,
                line = lineAt(text, e.offset),
                cause = e,
            )
        } catch (e: Exception) {
            throw MalformedSubscriptionException(DocumentFormat.JSON, cause = e)
        }
        val outbounds = root["outbounds"] as? List<*> ?: return ParseOutcome(emptyList())
        val profiles = outbounds.mapNotNull { entry -> (entry as? Map<*, *>)?.toProfile() }
        return ParseOutcome(profiles, dropped = outbounds.size - profiles.size)
    }

    private fun Map<*, *>.toProfile(): Profile? {
        val type = this["type"] as? String ?: return null
        val server = (this["server"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val serverPorts = (this["server_ports"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim() }
            ?.mapNotNull { range ->
                val separator = range.indexOfFirst { c -> c == ':' || c == '-' }
                if (separator <= 0) return@mapNotNull null
                val start = range.substring(0, separator).toIntOrNull() ?: return@mapNotNull null
                val end = range.substring(separator + 1).toIntOrNull() ?: return@mapNotNull null
                if (start !in 1..65535 || end !in 1..65535 || start > end) return@mapNotNull null
                "$start:$end"
            }
            .orEmpty()
        // A hopping node may carry no server_port at all; the first range's
        // start is the best stand-in for the profile's single port field.
        val port = (this["server_port"] as? Number)?.toInt()?.takeIf { it in 1..65535 }
            ?: serverPorts.firstOrNull()?.substringBefore(':')?.toIntOrNull()
            ?: return null
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
                Hysteria2Settings(
                    password = password,
                    tls = tls,
                    serverPorts = serverPorts,
                    hopInterval = (this["hop_interval"] as? String)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() },
                )
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
     *   "utls":{"enabled":true,"fingerprint":"chrome"},
     *   "reality":{"enabled":true,"public_key":"…","short_id":"…"}}`
     *
     * REALITY maps when the block carries a public key: that is the one field
     * the handshake cannot do without, and the rest of the block (SNI, uTLS
     * fingerprint, ALPN) is already the same fields as plain TLS. A REALITY
     * block without a key is still dropped by [toProfile] rather than imported
     * half-mapped.
     */
    private fun Map<*, *>.tlsOptions(): TlsOptions? {
        val tls = this["tls"] as? Map<*, *> ?: return TlsOptions.None
        if (tls["enabled"] != true) return TlsOptions.None
        val utls = tls["utls"] as? Map<*, *>
        val reality = (tls["reality"] as? Map<*, *>)?.takeIf { it["enabled"] != false }
        val publicKey = reality?.let {
            (it["public_key"] as? String)?.trim()?.takeIf { key -> key.isNotEmpty() }
                ?: return null
        }
        return TlsOptions(
            security = if (publicKey != null) SecurityMode.REALITY else SecurityMode.TLS,
            sni = (tls["server_name"] as? String)?.takeIf { it.isNotBlank() },
            alpn = (tls["alpn"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            fingerprint = (utls?.get("fingerprint") as? String)?.takeIf { it.isNotBlank() },
            allowInsecure = tls["insecure"] == true,
            ech = (tls["ech"] as? Map<*, *>)?.let { ech ->
                EchOptions(
                    enabled = ech["enabled"] != false,
                    config = (ech["config"] as? List<*>)?.filterIsInstance<String>()
                        ?.takeIf { it.isNotEmpty() },
                    queryServerName = (ech["query_server_name"] as? String)
                        ?.takeIf { it.isNotBlank() },
                )
            },
            reality = publicKey?.let {
                RealityOptions(
                    publicKey = it,
                    shortId = (reality?.get("short_id") as? String)
                        ?.trim()
                        ?.takeIf { id -> id.isNotEmpty() },
                    // sing-box has no field for an ML-KEM key, only this
                    // switch — the same one the config builder writes.
                    mlKem = reality?.get("support_x25519mlkem768") == true,
                )
            },
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
        // A block that states the host only as the TLS server name has the same
        // gap the link parser had: no Host header, and Cloudflare answers 403.
        val tls = this["tls"] as? Map<*, *>
        val serverName = (tls?.get("server_name") as? String)?.takeIf { it.isNotBlank() }
        return TransportOptions(
            type = type,
            host = headers["Host"] ?: headers["host"] ?: serverName,
            path = (transport["path"] as? String)?.takeIf { it.isNotBlank() },
            serviceName = (transport["service_name"] as? String)?.takeIf { it.isNotBlank() },
            maxEarlyData = (transport["max_early_data"] as? Number)?.toInt()
                ?.takeIf { it > 0 },
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
