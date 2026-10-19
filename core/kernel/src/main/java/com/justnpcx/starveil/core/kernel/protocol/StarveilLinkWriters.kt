package com.justnpcx.starveil.core.kernel.protocol

import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileSettings
import com.justnpcx.starveil.core.model.SecurityMode
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
 * The inverse of the link parsers: a [Profile] back into something shareable.
 *
 * One writer per protocol, registered on the descriptor exactly like
 * `ProtocolDescriptor.linkParser` — adding a protocol means registering both
 * halves, never adding a branch here.
 *
 * Every writer is held to one property: whatever it emits, the matching parser
 * reads back into an equal profile. That is the whole contract, and the tests
 * pin it protocol by protocol. A link nothing can import is worse than no link.
 */
internal object StarveilLinkWriters {

    fun vless(profile: Profile): String? {
        val s = profile.settings as? VlessSettings ?: return null
        if (s.uuid.isBlank()) return null
        val query = query(
            *tlsParams(s.tls, s.transport),
            "flow" to s.flow,
            *transportParams(s.transport),
            *muxParams(s),
        )
        return "vless://${LinkParsing.percentEncode(s.uuid)}@${authority(profile)}$query${fragment(profile)}"
    }

    fun trojan(profile: Profile): String? {
        val s = profile.settings as? TrojanSettings ?: return null
        if (s.password.isBlank()) return null
        val query = query(
            *tlsParams(s.tls, s.transport),
            *transportParams(s.transport),
            *muxParams(s),
        )
        return "trojan://${LinkParsing.percentEncode(s.password)}@${authority(profile)}$query${fragment(profile)}"
    }

    /**
     * SIP002: `ss://base64url(method:password)@host:port#name`.
     *
     * The userinfo is base64 rather than percent-encoded because that is what
     * SIP002 says and what every client expects to find there.
     */
    fun shadowsocks(profile: Profile): String? {
        val s = profile.settings as? ShadowsocksSettings ?: return null
        if (s.method.isBlank() || s.password.isBlank()) return null
        val userinfo = LinkParsing.base64EncodeUrl("${s.method}:${s.password}")
        return "ss://$userinfo@${authority(profile)}${fragment(profile)}"
    }

    /**
     * The v2rayN shape: `vmess://base64(json)`.
     *
     * Chosen over the URL-shaped form because it is the one that can carry a
     * label (`ps`), and a shared node with no name is not much use.
     */
    fun vmess(profile: Profile): String? {
        val s = profile.settings as? VmessSettings ?: return null
        if (s.uuid.isBlank()) return null
        val json = buildString {
            append('{')
            field("v", "2")
            field("ps", profile.name)
            field("add", profile.server)
            field("port", profile.port.toString())
            field("id", s.uuid)
            field("aid", s.alterId.toString())
            field("scy", s.security)
            field("net", s.transport.type.wireName)
            s.transport.host?.let { field("host", it) }
            s.transport.path?.let { field("path", it) }
            // In this form `tls` is "" or "tls" — the parser maps it straight
            // onto `security`.
            field("tls", if (s.tls.security == SecurityMode.NONE) "" else s.tls.security.wire())
            s.tls.sni?.let { field("sni", it) }
            if (s.tls.alpn.isNotEmpty()) field("alpn", s.tls.alpn.joinToString(","))
            s.tls.fingerprint?.let { field("fp", it) }
            if (s.tls.allowInsecure) field("allowInsecure", "1")
            s.tls.reality?.let {
                field("pbk", it.publicKey)
                it.shortId?.let { sid -> field("sid", sid) }
                if (it.mlKem) field("mlkem", "1")
            }
            trimTrailingComma()
            append('}')
        }
        return "vmess://${LinkParsing.base64Encode(json)}"
    }

    fun hysteria2(profile: Profile): String? {
        val s = profile.settings as? Hysteria2Settings ?: return null
        if (s.password.isBlank()) return null
        val query = query(
            *tlsParams(s.tls, TransportOptions.Tcp),
            "obfs" to s.obfs?.type,
            "obfs-password" to s.obfs?.password,
            "up" to s.upMbps?.toString(),
            "down" to s.downMbps?.toString(),
            "mport" to s.serverPorts.takeIf { it.isNotEmpty() }?.joinToString(","),
            "hop-interval" to s.hopInterval,
        )
        return "hysteria2://${LinkParsing.percentEncode(s.password)}@${authority(profile)}$query" +
            fragment(profile)
    }

    fun tuic(profile: Profile): String? {
        val s = profile.settings as? TuicSettings ?: return null
        if (s.uuid.isBlank() || s.password.isBlank()) return null
        // Both live in the userinfo, which is what the parser reads back.
        val userinfo = LinkParsing.percentEncode("${s.uuid}:${s.password}")
        val query = query(
            *tlsParams(s.tls, TransportOptions.Tcp),
            "congestion_control" to s.congestionControl?.wireName,
            "udp_relay_mode" to s.udpRelayMode?.wireName,
            "zero_rtt_handshake" to if (s.zeroRttHandshake) "1" else null,
        )
        return "tuic://$userinfo@${authority(profile)}$query${fragment(profile)}"
    }

    /**
     * WireGuard has no share URI — the format providers hand out is a
     * `wg-quick` `.conf`, and that is what gets shared.
     *
     * It round-trips through the parser's sniffing path rather than a scheme,
     * which is why the registry marks this descriptor as sniffable.
     */
    fun wireGuard(profile: Profile): String? {
        val s = profile.settings as? WireGuardSettings ?: return null
        if (s.privateKey.isBlank() || s.address.isEmpty() || s.peers.isEmpty()) return null
        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = ${s.privateKey}")
            appendLine("Address = ${s.address.joinToString(", ")}")
            s.mtu?.let { appendLine("MTU = $it") }
            s.listenPort?.let { appendLine("ListenPort = $it") }
            for (peer in s.peers) {
                appendLine()
                appendLine("[Peer]")
                appendLine("PublicKey = ${peer.publicKey}")
                peer.preSharedKey?.let { appendLine("PreSharedKey = $it") }
                peer.allowedIps.takeIf { it.isNotEmpty() }
                    ?.let { appendLine("AllowedIPs = ${it.joinToString(", ")}") }
                appendLine("Endpoint = ${peer.endpointAddress}:${peer.endpointPort}")
                peer.persistentKeepalive?.let { appendLine("PersistentKeepalive = $it") }
            }
        }.trimEnd() + "\n"
    }
}

// ---------------------------------------------------------------- shared bits

private fun authority(profile: Profile): String = "${profile.server}:${profile.port}"

/** The `#label` tail. Omitted when the name is just the address again. */
private fun fragment(profile: Profile): String {
    val name = profile.name.takeIf { it.isNotBlank() && it != authority(profile) } ?: return ""
    return "#${LinkParsing.percentEncode(name)}"
}

private fun SecurityMode.wire(): String = when (this) {
    SecurityMode.NONE -> "none"
    SecurityMode.TLS -> "tls"
    SecurityMode.REALITY -> "reality"
}

/**
 * Renders a query string. A null value is left out; an **empty string is kept**
 * as `key=`, which matters for exactly one case — see [tlsParams].
 */
private fun query(vararg pairs: Pair<String, String?>): String {
    val rendered = pairs.mapNotNull { (key, value) ->
        value?.let { "$key=${LinkParsing.percentEncode(it)}" }
    }
    return if (rendered.isEmpty()) "" else "?" + rendered.joinToString("&")
}

private fun tlsParams(tls: TlsOptions, transport: TransportOptions): Array<Pair<String, String?>> {
    val sni: Pair<String, String?> = when {
        // Written out blank on purpose. The parser falls back to the transport
        // `host` when a link says nothing about the SNI, so a node that has a
        // host but no SNI would come back with one it never had. An empty
        // parameter is the only way to say "there is none".
        tls.sni == null && tls.security != SecurityMode.NONE && transport.host != null ->
            "sni" to ""

        else -> "sni" to tls.sni
    }
    return arrayOf(
        "security" to tls.security.wire(),
        sni,
        "alpn" to tls.alpn.takeIf { it.isNotEmpty() }?.joinToString(","),
        "fp" to tls.fingerprint,
        "allowInsecure" to if (tls.allowInsecure) "1" else null,
        "pbk" to tls.reality?.publicKey,
        "sid" to tls.reality?.shortId,
        "support-x25519mlkem768" to if (tls.reality?.mlKem == true) "true" else null,
    )
}

/** Plain TCP is the parser's default, so it emits nothing at all. */
private fun transportParams(transport: TransportOptions): Array<Pair<String, String?>> {
    if (transport.type == TransportType.TCP) return emptyArray()
    return arrayOf(
        "type" to transport.type.wireName,
        "host" to transport.host,
        "path" to transport.path,
        "serviceName" to transport.serviceName,
        "mode" to transport.mode?.wireName,
    )
}

private fun muxParams(settings: ProfileSettings): Array<Pair<String, String?>> {
    val mux = when (settings) {
        is VlessSettings -> settings.mux
        is TrojanSettings -> settings.mux
        is VmessSettings -> settings.mux
        else -> null
    }
    return if (mux?.enabled == true) {
        arrayOf(
            "mux" to "1",
            "mux_protocol" to mux.protocol,
            // "0" rather than absent: an absent parameter parses back as "not
            // set", which is a different value from an explicit false.
            "mux_padding" to mux.padding?.let { if (it) "1" else "0" },
        )
    } else {
        emptyArray()
    }
}

// ------------------------------------------------------------- vmess JSON bits

private fun StringBuilder.field(key: String, value: String) {
    append('"').append(key).append("\":\"").append(jsonEscape(value)).append("\",")
}

/** `aid` and `port` are numbers in this format, but strings parse fine too. */
private fun StringBuilder.trimTrailingComma() {
    if (lastOrNull() == ',') deleteCharAt(length - 1)
}

private fun jsonEscape(value: String): String = buildString(value.length) {
    for (c in value) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
}
