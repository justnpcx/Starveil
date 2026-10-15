package com.justnpcx.starveil.core.kernel.protocol

import com.justnpcx.starveil.core.kernel.config.SingBoxOutbounds
import com.justnpcx.starveil.core.kernel.registry.ProtocolDescriptor
import com.justnpcx.starveil.core.model.MuxOptions
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

/**
 * Real descriptors for the three protocols in scope for M2:
 * VLESS, Trojan and Shadowsocks.
 *
 * Everything here is a pure function — parse a share link, or turn a
 * [Profile] into a sing-box outbound fragment. No IO, no logging of links or
 * settings (both carry credentials).
 *
 * The ML-KEM REALITY parameter name is a Starveil/Mirage fork extension and is
 * accepted under a couple of spellings; confirm the exact wire name against
 * the fork before shipping a build that relies on it.
 */
object StarveilProtocols {

    val Vless: ProtocolDescriptor = ProtocolDescriptor(
        type = ProfileType.VLESS,
        displayName = "VLESS",
        defaultPort = 443,
        schemes = listOf("vless"),
        linkParser = ::parseVless,
        configBuilder = { profile ->
            val settings = profile.settings as? VlessSettings
                ?: error("VLESS profile has no VLESS settings")
            SingBoxOutbounds.vless(profile, settings)
        },
        editorRoute = "editor/vless",
    )

    val Trojan: ProtocolDescriptor = ProtocolDescriptor(
        type = ProfileType.TROJAN,
        displayName = "Trojan",
        defaultPort = 443,
        schemes = listOf("trojan"),
        linkParser = ::parseTrojan,
        configBuilder = { profile ->
            val settings = profile.settings as? TrojanSettings
                ?: error("Trojan profile has no Trojan settings")
            SingBoxOutbounds.trojan(profile, settings)
        },
        editorRoute = "editor/trojan",
    )

    val Shadowsocks: ProtocolDescriptor = ProtocolDescriptor(
        type = ProfileType.SHADOWSOCKS,
        displayName = "Shadowsocks",
        defaultPort = 8388,
        schemes = listOf("ss"),
        linkParser = ::parseShadowsocks,
        configBuilder = { profile ->
            val settings = profile.settings as? ShadowsocksSettings
                ?: error("Shadowsocks profile has no Shadowsocks settings")
            SingBoxOutbounds.shadowsocks(profile, settings)
        },
        editorRoute = "editor/shadowsocks",
    )

    /** The three M2 descriptors, in the agreed priority order. */
    val all: List<ProtocolDescriptor> = listOf(Vless, Trojan, Shadowsocks)

    // ---------------------------------------------------------------- VLESS

    fun parseVless(link: String): Profile? {
        if (!link.startsWith("vless://", ignoreCase = true)) return null
        val (bodyNoFragment, name) = LinkParsing.splitFragment(link.substring(8))
        val (hostPart, queryRaw) = LinkParsing.splitQuery(bodyNoFragment)

        val at = hostPart.lastIndexOf('@')
        if (at <= 0) return null
        val uuid = LinkParsing.percentDecode(hostPart.substring(0, at)).trim()
        if (uuid.isBlank()) return null
        val (host, port) = LinkParsing.splitHostPort(hostPart.substring(at + 1)) ?: return null

        val q = LinkParsing.parseQuery(queryRaw)
        val security = securityFrom(q)
        val reality = realityFrom(q) ?: if (security == SecurityMode.REALITY) return null else null

        return Profile(
            name = name ?: "$host:$port",
            type = ProfileType.VLESS,
            server = host,
            port = port,
            settings = VlessSettings(
                uuid = uuid,
                flow = q["flow"]?.takeIf { it.isNotBlank() && it != "none" },
                tls = tlsFrom(q, security, reality),
                transport = transportFrom(q),
                mux = muxFrom(q),
            ),
        )
    }

    // --------------------------------------------------------------- Trojan

    fun parseTrojan(link: String): Profile? {
        if (!link.startsWith("trojan://", ignoreCase = true)) return null
        val (bodyNoFragment, name) = LinkParsing.splitFragment(link.substring(9))
        val (hostPart, queryRaw) = LinkParsing.splitQuery(bodyNoFragment)

        val at = hostPart.lastIndexOf('@')
        if (at <= 0) return null
        val password = LinkParsing.percentDecode(hostPart.substring(0, at))
        if (password.isBlank()) return null
        val (host, port) = LinkParsing.splitHostPort(hostPart.substring(at + 1)) ?: return null

        val q = LinkParsing.parseQuery(queryRaw)
        // Trojan is TLS-first; a link that says nothing about security is TLS.
        val security = when (q["security"]?.lowercase()) {
            "none" -> SecurityMode.NONE
            "reality" -> SecurityMode.REALITY
            else -> SecurityMode.TLS
        }
        val reality = realityFrom(q) ?: if (security == SecurityMode.REALITY) return null else null

        return Profile(
            name = name ?: "$host:$port",
            type = ProfileType.TROJAN,
            server = host,
            port = port,
            settings = TrojanSettings(
                password = password,
                tls = tlsFrom(q, security, reality),
                transport = transportFrom(q),
                mux = muxFrom(q),
            ),
        )
    }

    // ---------------------------------------------------------- Shadowsocks

    fun parseShadowsocks(link: String): Profile? {
        if (!link.startsWith("ss://", ignoreCase = true)) return null
        val (bodyNoFragment, name) = LinkParsing.splitFragment(link.substring(5))
        val (body, _) = LinkParsing.splitQuery(bodyNoFragment)

        // SIP002: ss://base64(method:password)@host:port
        val at = body.lastIndexOf('@')
        if (at > 0) {
            val userinfoRaw = body.substring(0, at)
            val userinfo = LinkParsing.base64Decode(userinfoRaw)
                ?: LinkParsing.percentDecode(userinfoRaw)
            val (host, port) = LinkParsing.splitHostPort(body.substring(at + 1)) ?: return null
            val (method, password) = splitMethodPassword(userinfo) ?: return null
            return ssProfile(name, host, port, method, password)
        }

        // Legacy: ss://base64(method:password@host:port)
        val decoded = LinkParsing.base64Decode(body) ?: return null
        val at2 = decoded.lastIndexOf('@')
        if (at2 <= 0) return null
        val (method, password) = splitMethodPassword(decoded.substring(0, at2)) ?: return null
        val (host, port) = LinkParsing.splitHostPort(decoded.substring(at2 + 1)) ?: return null
        return ssProfile(name, host, port, method, password)
    }

    private fun ssProfile(name: String?, host: String, port: Int, method: String, password: String) =
        Profile(
            name = name ?: "$host:$port",
            type = ProfileType.SHADOWSOCKS,
            server = host,
            port = port,
            settings = ShadowsocksSettings(method = method, password = password),
        )

    private fun splitMethodPassword(s: String): Pair<String, String>? {
        val colon = s.indexOf(':')
        if (colon <= 0) return null
        val method = s.substring(0, colon).trim()
        val password = s.substring(colon + 1)
        if (method.isBlank() || password.isBlank()) return null
        return method to password
    }

    // ------------------------------------------------------------ shared bits

    private fun securityFrom(q: Map<String, String>): SecurityMode =
        when (q["security"]?.lowercase()) {
            "tls" -> SecurityMode.TLS
            "reality" -> SecurityMode.REALITY
            else -> SecurityMode.NONE
        }

    private fun realityFrom(q: Map<String, String>): RealityOptions? {
        val publicKey = q["pbk"] ?: q["publicKey"] ?: return null
        if (publicKey.isBlank()) return null
        // `spx` is the ML-KEM-768 public key in a REALITY share link; the other
        // spellings are seen in the wild too. Its presence is what tells us the
        // server offers the hybrid handshake.
        val mlKem = q["spx"] ?: q["mlkem"] ?: q["ml-kem"] ?: q["mlkem-pbk"]
        return RealityOptions(
            publicKey = publicKey,
            shortId = q["sid"]?.takeIf { it.isNotBlank() },
            mlKemPublicKey = mlKem?.takeIf { it.isNotBlank() },
        )
    }

    private fun tlsFrom(q: Map<String, String>, security: SecurityMode, reality: RealityOptions?) =
        TlsOptions(
            security = security,
            sni = (q["sni"] ?: q["peer"] ?: if (security != SecurityMode.NONE) q["host"] else null)
                ?.takeIf { it.isNotBlank() },
            alpn = LinkParsing.asAlpn(q["alpn"]),
            fingerprint = q["fp"]?.takeIf { it.isNotBlank() },
            allowInsecure = LinkParsing.asBool(q["allowInsecure"] ?: q["insecure"]),
            reality = reality,
        )

    private fun transportFrom(q: Map<String, String>): TransportOptions {
        val type = when (q["type"]?.lowercase()) {
            "ws" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "http", "h2" -> TransportType.HTTP
            "xhttp" -> TransportType.XHTTP
            else -> TransportType.TCP
        }
        return TransportOptions(
            type = type,
            host = q["host"]?.takeIf { it.isNotBlank() },
            path = q["path"]?.takeIf { it.isNotBlank() },
            serviceName = (q["serviceName"] ?: q["servicename"])?.takeIf { it.isNotBlank() },
        )
    }

    private fun muxFrom(q: Map<String, String>): MuxOptions? {
        val raw = q["mux"] ?: return null
        val enabled = LinkParsing.asBool(raw) || (raw.toIntOrNull() ?: 0) > 0
        if (!enabled) return null
        return MuxOptions(enabled = true)
    }
}
