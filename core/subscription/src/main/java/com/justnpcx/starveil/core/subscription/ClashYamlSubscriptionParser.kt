package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.model.Hysteria2Settings
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
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.MarkedYAMLException

/**
 * Parses a Clash-style subscription (`proxies:` list) into [Profile]s.
 *
 * Security: SnakeYAML is used with [SafeConstructor] only, so a malicious
 * subscription can never instantiate arbitrary Java objects. Loader limits are
 * tightened against alias bombs. The document is never logged (credentials).
 *
 * Scope: vless, vmess, trojan, shadowsocks, hysteria2 and tuic — every protocol
 * this app can build. Anything else (wireguard, ssh, …) is skipped rather than
 * mis-mapped.
 */
class ClashYamlSubscriptionParser : SubscriptionParser {

    override fun parse(text: String): List<Profile> = try {
        parseDetailed(text).profiles
    } catch (e: MalformedSubscriptionException) {
        // The simple form stays total; only the detailed form reports why.
        emptyList()
    }

    /**
     * Every proxy entry that produced no profile counts as dropped — including
     * a protocol this app cannot build, and an entry of a known protocol whose
     * required fields are missing. That is the honest number: those nodes exist
     * in the subscription and are not in the list, and the user is the one who
     * needs to know.
     */
    override fun parseDetailed(text: String): ParseOutcome {
        val root = try {
            yaml().load<Any?>(text)
        } catch (e: MarkedYAMLException) {
            // SnakeYAML's mark is 0-based; a reader counts from 1. The problem
            // text itself is not carried: it can quote the offending line, and
            // a subscription body is credentials.
            throw MalformedSubscriptionException(
                format = DocumentFormat.YAML,
                line = e.problemMark?.line?.plus(1),
                cause = e,
            )
        } catch (e: Exception) {
            throw MalformedSubscriptionException(DocumentFormat.YAML, cause = e)
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
                        tls = tlsFrom(proxy, defaultSecurity = SecurityMode.NONE),
                        transport = transportFrom(proxy),
                    ),
                )
            }

            "vmess" -> {
                val uuid = proxy.str("uuid") ?: return null
                Profile(
                    name = name,
                    type = ProfileType.VMESS,
                    server = server,
                    port = port,
                    settings = VmessSettings(
                        uuid = uuid,
                        security = proxy.str("cipher") ?: proxy.str("security") ?: "auto",
                        alterId = proxy.int("alterId") ?: proxy.int("alter-id") ?: 0,
                        tls = tlsFrom(proxy, defaultSecurity = SecurityMode.NONE),
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

            "hysteria2", "hy2" -> {
                val password = proxy.str("password") ?: return null
                val obfs = proxy.str("obfs")
                    ?.takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
                    ?.let { ObfsOptions(type = it, password = proxy.str("obfs-password").orEmpty()) }
                Profile(
                    name = name,
                    type = ProfileType.HYSTERIA2,
                    server = server,
                    port = port,
                    settings = Hysteria2Settings(
                        password = password,
                        obfs = obfs,
                        // QUIC: always TLS, never a stream transport.
                        tls = tlsFrom(proxy, defaultSecurity = SecurityMode.TLS),
                        serverPorts = portRanges(proxy.str("ports")),
                    ),
                )
            }

            "tuic" -> {
                val uuid = proxy.str("uuid") ?: return null
                val password = proxy.str("password") ?: return null
                Profile(
                    name = name,
                    type = ProfileType.TUIC,
                    server = server,
                    port = port,
                    settings = TuicSettings(
                        uuid = uuid,
                        password = password,
                        congestionControl = proxy.str("congestion-controller")
                            ?.let { TuicCongestionControl.fromWireName(it) },
                        udpRelayMode = proxy.str("udp-relay-mode")
                            ?.let { TuicUdpRelayMode.fromWireName(it) },
                        zeroRttHandshake = proxy.bool("reduce-rtt") ?: false,
                        tls = tlsFrom(proxy, defaultSecurity = SecurityMode.TLS),
                    ),
                )
            }

            // A protocol this app cannot build: skipped, and counted as dropped.
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

    /**
     * Clash writes port hopping as `ports: 20000-30000` or `443,8443`; the
     * model carries sing-box's `start:end` form. An unreadable token is
     * dropped rather than guessed at.
     */
    private fun portRanges(raw: String?): List<String> = raw.orEmpty()
        .split(',')
        .mapNotNull { token ->
            val text = token.trim()
            when {
                text.isEmpty() -> null
                '-' in text -> {
                    val bounds = text.split('-', limit = 2).map { it.trim().toIntOrNull() }
                    val start = bounds.getOrNull(0)
                    val end = bounds.getOrNull(1)
                    if (start != null && end != null && start in 1..65535 && end in 1..65535) {
                        "$start:$end"
                    } else {
                        null
                    }
                }

                else -> text.toIntOrNull()?.takeIf { it in 1..65535 }?.toString()
            }
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
