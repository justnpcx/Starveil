package com.justnpcx.starveil.core.kernel.protocol

import com.justnpcx.starveil.core.kernel.config.SingBoxOutbounds
import com.justnpcx.starveil.core.kernel.registry.ConfigPlacement
import com.justnpcx.starveil.core.kernel.registry.ProtocolDescriptor
import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.MuxOptions
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
import com.justnpcx.starveil.core.model.WireGuardPeer
import com.justnpcx.starveil.core.model.WireGuardSettings
import com.justnpcx.starveil.core.model.XhttpMode
import com.justnpcx.starveil.core.model.json.Json
import com.justnpcx.starveil.core.model.json.JsonException

/**
 * Real descriptors for the protocols in scope for M2 — VLESS, Trojan and
 * Shadowsocks — plus VMess.
 *
 * Everything here is a pure function — parse a share link, or turn a
 * [Profile] into a sing-box outbound fragment. No IO, no logging of links or
 * settings (both carry credentials).
 *
 * The ML-KEM REALITY parameter is a Starveil fork extension: the share
 * link carries the key as `spx`, and it maps to sing-box's
 * `support_x25519mlkem768` switch (there is no field for the key itself).
 */
object StarveilProtocols {

    val Vless: ProtocolDescriptor = ProtocolDescriptor(
        type = ProfileType.VLESS,
        editorRoute = "editor/vless",
        displayName = "VLESS",
        defaultPort = 443,
        schemes = listOf("vless"),
        linkParser = ::parseVless,
        linkWriter = StarveilLinkWriters::vless,
        configBuilder = { profile ->
            val settings = profile.settings as? VlessSettings
                ?: error("VLESS profile has no VLESS settings")
            SingBoxOutbounds.vless(profile, settings)
        },
    )

    val Trojan: ProtocolDescriptor = ProtocolDescriptor(
        type = ProfileType.TROJAN,
        editorRoute = "editor/trojan",
        displayName = "Trojan",
        defaultPort = 443,
        schemes = listOf("trojan"),
        linkParser = ::parseTrojan,
        linkWriter = StarveilLinkWriters::trojan,
        configBuilder = { profile ->
            val settings = profile.settings as? TrojanSettings
                ?: error("Trojan profile has no Trojan settings")
            SingBoxOutbounds.trojan(profile, settings)
        },
    )

    val Shadowsocks: ProtocolDescriptor = ProtocolDescriptor(
        type = ProfileType.SHADOWSOCKS,
        editorRoute = "editor/shadowsocks",
        displayName = "Shadowsocks",
        defaultPort = 8388,
        schemes = listOf("ss"),
        linkParser = ::parseShadowsocks,
        linkWriter = StarveilLinkWriters::shadowsocks,
        configBuilder = { profile ->
            val settings = profile.settings as? ShadowsocksSettings
                ?: error("Shadowsocks profile has no Shadowsocks settings")
            SingBoxOutbounds.shadowsocks(profile, settings)
        },
    )

    val Vmess: ProtocolDescriptor = ProtocolDescriptor(
        type = ProfileType.VMESS,
        editorRoute = "editor/vmess",
        displayName = "VMess",
        defaultPort = 443,
        schemes = listOf("vmess"),
        linkParser = ::parseVmess,
        linkWriter = StarveilLinkWriters::vmess,
        configBuilder = { profile ->
            val settings = profile.settings as? VmessSettings
                ?: error("VMess profile has no VMess settings")
            SingBoxOutbounds.vmess(profile, settings)
        },
    )

    val Hysteria2: ProtocolDescriptor = ProtocolDescriptor(
        type = ProfileType.HYSTERIA2,
        editorRoute = "editor/hysteria2",
        displayName = "Hysteria2",
        defaultPort = 443,
        schemes = listOf("hysteria2", "hy2"),
        linkParser = ::parseHysteria2,
        linkWriter = StarveilLinkWriters::hysteria2,
        configBuilder = { profile ->
            val settings = profile.settings as? Hysteria2Settings
                ?: error("Hysteria2 profile has no Hysteria2 settings")
            SingBoxOutbounds.hysteria2(profile, settings)
        },
    )

    val Tuic: ProtocolDescriptor = ProtocolDescriptor(
        type = ProfileType.TUIC,
        editorRoute = "editor/tuic",
        displayName = "TUIC",
        defaultPort = 443,
        schemes = listOf("tuic"),
        linkParser = ::parseTuic,
        linkWriter = StarveilLinkWriters::tuic,
        configBuilder = { profile ->
            val settings = profile.settings as? TuicSettings
                ?: error("TUIC profile has no TUIC settings")
            SingBoxOutbounds.tuic(profile, settings)
        },
    )

    val WireGuard: ProtocolDescriptor = ProtocolDescriptor(
        type = ProfileType.WIREGUARD,
        editorRoute = "editor/wireguard",
        displayName = "WireGuard",
        defaultPort = 51820,
        schemes = listOf("wireguard"),
        linkParser = ::parseWireGuardConf,
        linkWriter = StarveilLinkWriters::wireGuard,
        configBuilder = { profile ->
            val settings = profile.settings as? WireGuardSettings
                ?: error("WireGuard profile has no WireGuard settings")
            SingBoxOutbounds.wireguard(profile, settings)
        },
        // No URI scheme to key on: a .conf is recognised by its sections.
        sniff = { text ->
            text.contains("[Interface]", ignoreCase = true) &&
                text.contains("[Peer]", ignoreCase = true)
        },
        // sing-box 1.13 removed the WireGuard outbound; it is an endpoint now.
        placement = ConfigPlacement.ENDPOINT,
    )

    /** The real descriptors, in the agreed priority order. */
    val all: List<ProtocolDescriptor> =
        listOf(Vless, Trojan, Shadowsocks, Vmess, Hysteria2, Tuic, WireGuard)

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

    // ---------------------------------------------------------------- VMess

    /**
     * Two shapes of `vmess://` are in the wild:
     *
     * - the original base64 of a JSON blob (v2rayN), with the fields as
     *   `add`/`port`/`id`/`aid`/`net`/`tls`;
     * - a URL-shaped link (v2rayNG) with the UUID in the userinfo and the rest
     *   in the query string, exactly like VLESS.
     *
     * Base64 has no `@`, so its presence is what tells them apart.
     */
    fun parseVmess(link: String): Profile? {
        if (!link.startsWith("vmess://", ignoreCase = true)) return null
        val (body, name) = LinkParsing.splitFragment(link.substring(8))
        if (body.isBlank()) return null
        return if (body.contains('@')) parseVmessUrl(body, name) else parseVmessJson(body, name)
    }

    private fun parseVmessUrl(body: String, name: String?): Profile? {
        val (hostPart, queryRaw) = LinkParsing.splitQuery(body)
        val at = hostPart.lastIndexOf('@')
        if (at <= 0) return null
        val uuid = LinkParsing.percentDecode(hostPart.substring(0, at)).trim()
        if (uuid.isBlank()) return null
        val (host, port) = LinkParsing.splitHostPort(hostPart.substring(at + 1)) ?: return null
        return vmessProfile(name, host, port, uuid, LinkParsing.parseQuery(queryRaw))
    }

    private fun parseVmessJson(body: String, name: String?): Profile? {
        val decoded = LinkParsing.base64Decode(body) ?: return null
        val map = try {
            Json.parseObject(decoded)
        } catch (e: JsonException) {
            return null
        }
        val host = map.vmessStr("add") ?: return null
        val port = map.vmessInt("port") ?: return null
        val uuid = map.vmessStr("id") ?: return null
        if (port !in 1..65535) return null
        // `ps` (the label) is inside the JSON for this form.
        return vmessProfile(name ?: map.vmessStr("ps"), host, port, uuid, map.vmessParams())
    }

    /**
     * Normalises the JSON form's field names onto the ones the URL form uses,
     * so one builder covers both.
     */
    private fun Map<String, Any?>.vmessParams(): Map<String, String> {
        val q = LinkedHashMap<String, String>()
        vmessStr("net")?.let { q["type"] = it }
        vmessStr("scy")?.let { q["encryption"] = it }
        vmessInt("aid")?.let { q["aid"] = it.toString() }
        vmessStr("host")?.let { q["host"] = it }
        vmessStr("path")?.let { q["path"] = it }
        vmessStr("sni")?.let { q["sni"] = it }
        vmessStr("alpn")?.let { q["alpn"] = it }
        vmessStr("fp")?.let { q["fp"] = it }
        // In the JSON form `tls` is either "" or "tls".
        vmessStr("tls")?.let { q["security"] = it }
        return q
    }

    private fun vmessProfile(
        name: String?,
        host: String,
        port: Int,
        uuid: String,
        q: Map<String, String>,
    ): Profile? {
        val security = when (q["security"]?.lowercase()) {
            "tls" -> SecurityMode.TLS
            "reality" -> SecurityMode.REALITY
            else -> SecurityMode.NONE
        }
        val reality = realityFrom(q)
        // REALITY without a public key cannot be dialled; refuse rather than
        // build a config that would fail at connect time.
        if (security == SecurityMode.REALITY && reality == null) return null

        return Profile(
            name = name ?: "$host:$port",
            type = ProfileType.VMESS,
            server = host,
            port = port,
            settings = VmessSettings(
                uuid = uuid,
                security = q["encryption"]?.takeIf { it.isNotBlank() } ?: "auto",
                alterId = q["aid"]?.trim()?.toIntOrNull() ?: 0,
                tls = tlsFrom(q, security, reality),
                transport = transportFrom(q),
                mux = muxFrom(q),
            ),
        )
    }

    private fun Map<String, Any?>.vmessStr(key: String): String? =
        when (val value = this[key]) {
            is String -> value.takeIf { it.isNotBlank() }
            is Number -> value.toString()
            else -> null
        }

    private fun Map<String, Any?>.vmessInt(key: String): Int? =
        when (val value = this[key]) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }

    // ------------------------------------------------------------- Hysteria2

    /**
     * `hysteria2://password@host:port/?sni=…&obfs=salamander&obfs-password=…`
     *
     * Two details this format gets wrong easily:
     *
     * - the URL form puts a `/` (usually with nothing after it) between the
     *   authority and the query, so `host:port` has to be cut at the first `/`
     *   or the port parses as `443/`;
     * - a server that authenticates with `user:pass` carries both in the
     *   userinfo, and sing-box has a single password field — so the whole
     *   userinfo is the password.
     */
    fun parseHysteria2(link: String): Profile? {
        val schemeLength = when {
            link.startsWith("hysteria2://", ignoreCase = true) -> 12
            link.startsWith("hy2://", ignoreCase = true) -> 6
            else -> return null
        }
        val (body, name) = LinkParsing.splitFragment(link.substring(schemeLength))
        val (hostPart, queryRaw) = LinkParsing.splitQuery(body)

        val at = hostPart.lastIndexOf('@')
        if (at <= 0) return null
        val password = LinkParsing.percentDecode(hostPart.substring(0, at))
        if (password.isBlank()) return null
        val authority = hostPart.substring(at + 1).substringBefore('/')
        val (host, port) = LinkParsing.splitHostPort(authority) ?: return null

        val q = LinkParsing.parseQuery(queryRaw)
        return Profile(
            name = name ?: "$host:$port",
            type = ProfileType.HYSTERIA2,
            server = host,
            port = port,
            settings = Hysteria2Settings(
                password = password,
                obfs = obfsFrom(q),
                upMbps = q.mbps("up", "upmbps"),
                downMbps = q.mbps("down", "downmbps"),
                // Hysteria2 runs over QUIC, so TLS is not optional — the
                // default here is plain TLS rather than none.
                tls = tlsFrom(q, SecurityMode.TLS, reality = null),
            ),
        )
    }

    private fun Map<String, String>.mbps(vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key ->
            this[key]?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        }

    /**
     * Only the obfs types sing-box still accepts, and only when the password
     * is present. An unknown type is dropped rather than passed through:
     * sing-box rejects the whole config on an unknown enum value, which would
     * stop the kernel instead of failing one connection.
     */
    private fun obfsFrom(q: Map<String, String>): ObfsOptions? {
        val type = q["obfs"]?.trim()?.lowercase()
            ?.takeIf { it.isNotBlank() && it != "none" }
            ?: return null
        if (type != "salamander" && type != "gecko") return null
        val password = (q["obfs-password"] ?: q["obfspassword"])?.takeIf { it.isNotBlank() }
            ?: return null
        return ObfsOptions(type = type, password = password)
    }

    // ------------------------------------------------------------------ TUIC

    /**
     * `tuic://uuid:password@host:port?congestion_control=bbr&udp_relay_mode=native&…`
     *
     * TUIC v5 authenticates with a UUID **and** a password, both in the
     * userinfo — a link with only one of them is not a TUIC v5 node and is
     * rejected rather than half-filled.
     */
    fun parseTuic(link: String): Profile? {
        if (!link.startsWith("tuic://", ignoreCase = true)) return null
        val (body, name) = LinkParsing.splitFragment(link.substring(7))
        val (hostPart, queryRaw) = LinkParsing.splitQuery(body)

        val at = hostPart.lastIndexOf('@')
        if (at <= 0) return null
        val userInfo = LinkParsing.percentDecode(hostPart.substring(0, at))
        val colon = userInfo.indexOf(':')
        if (colon <= 0) return null
        val uuid = userInfo.substring(0, colon).trim()
        val password = userInfo.substring(colon + 1)
        if (uuid.isBlank() || password.isBlank()) return null

        val authority = hostPart.substring(at + 1).substringBefore('/')
        val (host, port) = LinkParsing.splitHostPort(authority) ?: return null

        val q = LinkParsing.parseQuery(queryRaw)
        return Profile(
            name = name ?: "$host:$port",
            type = ProfileType.TUIC,
            server = host,
            port = port,
            settings = TuicSettings(
                uuid = uuid,
                password = password,
                // Both are constrained upstream; an unrecognised value is
                // dropped rather than forwarded, because sing-box rejects the
                // whole config on an unknown enum and the kernel would not start.
                congestionControl = q["congestion_control"]
                    ?.let { TuicCongestionControl.fromWireName(it) },
                udpRelayMode = q["udp_relay_mode"]?.let { TuicUdpRelayMode.fromWireName(it) },
                zeroRttHandshake = LinkParsing.asBool(q["zero_rtt_handshake"]),
                // QUIC: TLS is not optional.
                tls = tlsFrom(q, SecurityMode.TLS, reality = null),
            ),
        )
    }

    // ------------------------------------------------------------- WireGuard

    /**
     * A WireGuard `.conf` — the `wg-quick` format every provider hands out.
     *
     * There is no URI scheme to key on, so the registry matches this by
     * sniffing for the `[Interface]` / `[Peer]` sections instead.
     *
     * The file's `DNS` line is deliberately ignored: this app runs a tunnel
     * inside a tun that already hijacks DNS, so a second resolver would fight
     * it. `Address` is the tunnel address, which the endpoint does need.
     */
    fun parseWireGuardConf(text: String): Profile? {
        val sections = parseIni(text)
        val iface = sections.firstOrNull { it.first == "interface" }?.second ?: return null
        val privateKey = iface["privatekey"]?.takeIf { it.isNotBlank() } ?: return null
        val address = iface["address"].csv()
        if (address.isEmpty()) return null

        val peers = sections.filter { it.first == "peer" }.mapNotNull { (_, peer) ->
            val publicKey = peer["publickey"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // A peer with no endpoint cannot be dialled; skip it rather than
            // fail the whole import — a .conf can list several.
            val endpoint = peer["endpoint"] ?: return@mapNotNull null
            val (host, port) = LinkParsing.splitHostPort(endpoint) ?: return@mapNotNull null
            WireGuardPeer(
                publicKey = publicKey,
                endpointAddress = host,
                endpointPort = port,
                allowedIps = peer["allowedips"].csv(),
                preSharedKey = peer["presharedkey"]?.takeIf { it.isNotBlank() },
                persistentKeepalive = peer["persistentkeepalive"]
                    ?.trim()?.toIntOrNull()?.takeIf { it > 0 },
            )
        }
        if (peers.isEmpty()) return null

        val first = peers.first()
        return Profile(
            name = "WireGuard ${first.endpointAddress}:${first.endpointPort}",
            type = ProfileType.WIREGUARD,
            server = first.endpointAddress,
            port = first.endpointPort,
            settings = WireGuardSettings(
                privateKey = privateKey,
                address = address,
                peers = peers,
                mtu = iface["mtu"]?.trim()?.toIntOrNull()?.takeIf { it > 0 },
                listenPort = iface["listenport"]?.trim()?.toIntOrNull()?.takeIf { it > 0 },
            ),
        )
    }

    /**
     * `[section]` / `key = value`, with `#` and `;` comments. Sections repeat
     * (a `.conf` can carry several `[Peer]`s), so this returns a list rather
     * than a map keyed by section name.
     */
    private fun parseIni(text: String): List<Pair<String, Map<String, String>>> {
        val sections = mutableListOf<Pair<String, Map<String, String>>>()
        var current: MutableMap<String, String>? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                val name = line.substring(1, line.length - 1).trim().lowercase()
                current = LinkedHashMap()
                sections += name to current
                continue
            }
            val eq = line.indexOf('=')
            if (eq <= 0 || current == null) continue
            val key = line.substring(0, eq).trim().lowercase()
            val value = line.substring(eq + 1).trim()
            if (key.isNotEmpty()) current[key] = value
        }
        return sections
    }

    private fun String?.csv(): List<String> =
        this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

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
        // ML-KEM is a switch the server advertises, and the link says so
        // explicitly. `spx` is NOT the key — it is the SpiderX path (a path,
        // like /7a643a01ce631a9) — and treating its presence as the switch
        // only appeared to work because the two usually travel together.
        val mlKem = LinkParsing.asBool(q["support-x25519mlkem768"]) ||
            // Spellings seen in the wild, where the parameter carries the key
            // itself; for those, presence is the only signal available.
            !q["mlkem"].isNullOrBlank() ||
            !q["ml-kem"].isNullOrBlank() ||
            !q["mlkem-pbk"].isNullOrBlank()
        return RealityOptions(
            publicKey = publicKey,
            shortId = q["sid"]?.takeIf { it.isNotBlank() },
            mlKem = mlKem,
        )
    }

    private fun tlsFrom(q: Map<String, String>, security: SecurityMode, reality: RealityOptions?) =
        TlsOptions(
            security = security,
            sni = (q["sni"] ?: q["peer"] ?: if (security != SecurityMode.NONE) q["host"] else null)
                ?.takeIf { it.isNotBlank() },
            alpn = LinkParsing.asAlpn(q["alpn"]),
            fingerprint = q["fp"]?.takeIf { it.isNotBlank() },
            allowInsecure = LinkParsing.asBool(
                q["allowInsecure"] ?: q["insecure"] ?: q["allow_insecure"],
            ),
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
            // The link carries this as a plain `mode` parameter. Xray panels also
            // mirror it inside `extra={"mode":…}`; that copy is ignored because it
            // is the same value, and a mode we do not implement decodes to null so
            // the core falls back to its own default.
            mode = q["mode"]?.takeIf { it.isNotBlank() }?.let { XhttpMode.fromWireName(it) },
        )
    }

    private fun muxFrom(q: Map<String, String>): MuxOptions? {
        val raw = q["mux"] ?: return null
        val enabled = LinkParsing.asBool(raw) || (raw.toIntOrNull() ?: 0) > 0
        if (!enabled) return null
        return MuxOptions(
            enabled = true,
            // The link ecosystem treats mux as a client-side setting, so these
            // two names are this app's own extension. Harmless to other clients
            // — they ignore parameters they do not know — and the only way this
            // app's own export can come back whole.
            protocol = q["mux_protocol"]?.takeIf { it.isNotBlank() },
            padding = q["mux_padding"]?.let { LinkParsing.asBool(it) },
        )
    }
}
