package com.justnpcx.starveil.core.subscription

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
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Parses a Clash-style subscription (`proxies:` list) into [Profile]s.
 *
 * Security: SnakeYAML is used with [SafeConstructor] only, so a malicious
 * subscription can never instantiate arbitrary Java objects. Loader limits are
 * tightened against alias bombs. The document is never logged (credentials).
 *
 * M2 maps the three in-scope protocols (vless / trojan / ss); other types are
 * skipped rather than mis-mapped.
 */
class ClashYamlSubscriptionParser : SubscriptionParser {

    override fun parse(text: String): List<Profile> = parseDetailed(text).profiles

    /**
     * Every proxy entry that produced no profile counts as dropped — including
     * the protocols this app does not implement (vmess, hysteria2, tuic). That
     * is the honest number: those nodes exist in the subscription and are not
     * in the list, and the user is the one who needs to know.
     */
    override fun parseDetailed(text: String): ParseOutcome {
        val root = try {
            yaml().load<Any?>(text)
        } catch (e: Exception) {
            return ParseOutcome(emptyList())
        }
        val map = root as? Map<*, *> ?: return ParseOutcome(emptyList())
        val proxies = map["proxies"] as? List<*> ?: return ParseOutcome(emptyList())
        val profiles = proxies.mapNotNull { entry -> parseProxy(entry as? Map<*, *> ?: return@mapNotNull null) }
        return ParseOutcome(profiles, dropped = proxies.size - profiles.size)
    }

    private fun yaml(): Yaml {
        val options = LoaderOptions().apply {
            // Defensive limits: subscriptions are untrusted input.
            maxAliasesForCollections = 16
            codePointLimit = 4 * 1024 * 1024
        }
        return Yaml(SafeConstructor(options))
    }

    private fun parseProxy(proxy: Map<*, *>): Profile? {
        val type = proxy.str("type")?.lowercase() ?: return null
        val name = proxy.str("name") ?: return null
        val server = proxy.str("server") ?: return null
        val port = proxy.int("port") ?: return null
        if (server.isBlank() || port !in 1..65535) return null

        return when (type) {
            "vless" -> {
                val uuid = proxy.str("uuid") ?: return null
                Profile(
                    name = name,
                    type = ProfileType.VLESS,
                    server = server,
                    port = port,
                    settings = VlessSettings(
                        uuid = uuid,
                        flow = proxy.str("flow"),
                        tls = tlsFrom(proxy, defaultSecurity = SecurityMode.TLS),
                        transport = transportFrom(proxy),
                    ),
                )
            }

            "trojan" -> {
                val password = proxy.str("password") ?: return null
                Profile(
                    name = name,
                    type = ProfileType.TROJAN,
                    server = server,
                    port = port,
                    settings = TrojanSettings(
                        password = password,
                        tls = tlsFrom(proxy, defaultSecurity = SecurityMode.TLS),
                        transport = transportFrom(proxy),
                    ),
                )
            }

            "ss" -> {
                val method = proxy.str("cipher") ?: return null
                val password = proxy.str("password") ?: return null
                Profile(
                    name = name,
                    type = ProfileType.SHADOWSOCKS,
                    server = server,
                    port = port,
                    settings = ShadowsocksSettings(method = method, password = password),
                )
            }

            // Out of M2 scope (vmess / hysteria2 / tuic / wireguard …).
            else -> null
        }
    }

    private fun tlsFrom(proxy: Map<*, *>, defaultSecurity: SecurityMode): TlsOptions {
        val reality = (proxy["reality-opts"] as? Map<*, *>)?.let { r ->
            val publicKey = r.str("public-key") ?: return@let null
            RealityOptions(publicKey = publicKey, shortId = r.str("short-id"))
        }
        val security = when {
            reality != null -> SecurityMode.REALITY
            proxy.bool("tls") == true -> SecurityMode.TLS
            else -> defaultSecurity
        }
        return TlsOptions(
            security = security,
            sni = proxy.str("servername") ?: proxy.str("sni"),
            alpn = (proxy["alpn"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            fingerprint = proxy.str("client-fingerprint"),
            allowInsecure = proxy.bool("skip-cert-verify") ?: false,
            reality = reality,
        )
    }

    private fun transportFrom(proxy: Map<*, *>): TransportOptions {
        val network = proxy.str("network")?.lowercase()
        return when (network) {
            "ws" -> {
                val opts = proxy["ws-opts"] as? Map<*, *>
                val headers = (opts?.get("headers") as? Map<*, *>)
                    ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
                    ?.toMap()
                    ?: emptyMap()
                TransportOptions(
                    type = TransportType.WS,
                    path = opts?.str("path"),
                    headers = headers,
                )
            }

            "grpc" -> {
                val opts = proxy["grpc-opts"] as? Map<*, *>
                TransportOptions(type = TransportType.GRPC, serviceName = opts?.str("grpc-service-name"))
            }

            "h2" -> TransportOptions(type = TransportType.HTTP, host = proxy.str("host"), path = proxy.str("path"))
            else -> TransportOptions.Tcp
        }
    }

    companion object {
        /** Cheap, pure sniff: a top-level `proxies:` key. No IO. */
        fun sniff(text: String): Boolean =
            text.lineSequence().any { it.trimStart().startsWith("proxies:") }
    }

    // ----------------------------------------------------- tolerant accessors

    private fun Map<*, *>.str(key: String): String? = (this[key] as? String)?.takeIf { it.isNotEmpty() }

    private fun Map<*, *>.bool(key: String): Boolean? = this[key] as? Boolean

    private fun Map<*, *>.int(key: String): Int? =
        when (val v = this[key]) {
            is Int -> v
            is Number -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }
}
