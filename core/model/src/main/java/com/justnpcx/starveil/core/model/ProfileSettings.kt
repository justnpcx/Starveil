package com.justnpcx.starveil.core.model

/**
 * Per-protocol settings for a [Profile].
 *
 * Sealed on purpose: adding a protocol means adding one [ProfileSettings]
 * subtype plus one registry descriptor — never a new `when` branch in the
 * config builder or the UI.
 *
 * These are the *domain* shapes. They never carry the on-disk JSON form;
 * the data layer maps them to/from `ProfileEntity.settingsJson`.
 */
sealed interface ProfileSettings

/** How the outbound secures its transport. */
enum class SecurityMode { NONE, TLS, REALITY }

/** Stream transport. [XHTTP] is the Starveil/Mirage fork's addition. */
enum class TransportType(val wireName: String) {
    TCP("tcp"),
    WS("ws"),
    GRPC("grpc"),
    HTTP("http"),
    XHTTP("xhttp"),
}

/**
 * REALITY options.
 *
 * [mlKemPublicKey] is the server's post-quantum (ML-KEM-768) key, carried by
 * the `spx` parameter of a REALITY share link. sing-box has no config field
 * for the key itself — the client derives nothing from it — so its only job
 * here is to say whether the server supports the hybrid handshake, which is
 * what `support_x25519mlkem768` tells sing-box. Absent means the node is
 * plain X25519.
 */
data class RealityOptions(
    val publicKey: String,
    val shortId: String? = null,
    val mlKemPublicKey: String? = null,
)

/**
 * TLS / uTLS options shared by every TLS-capable protocol.
 * [RealityOptions] is ignored unless [security] is [SecurityMode.REALITY].
 */
data class TlsOptions(
    val security: SecurityMode = SecurityMode.NONE,
    val sni: String? = null,
    val alpn: List<String> = emptyList(),
    val fingerprint: String? = null,
    val allowInsecure: Boolean = false,
    val reality: RealityOptions? = null,
) {
    val enabled: Boolean get() = security != SecurityMode.NONE

    companion object {
        /** No TLS at all (plain TCP). */
        val None = TlsOptions()

        /** Plain TLS, no uTLS fingerprint. */
        val Plain = TlsOptions(security = SecurityMode.TLS)
    }
}

/** Stream transport options. Fields that do not apply are simply null. */
data class TransportOptions(
    val type: TransportType = TransportType.TCP,
    val host: String? = null,
    val path: String? = null,
    /** gRPC service name. */
    val serviceName: String? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        /** Plain TCP — no `transport` block is emitted for this. */
        val Tcp = TransportOptions()
    }
}

/** Multiplexing options. `null` on a profile means "not configured". */
data class MuxOptions(
    val enabled: Boolean = false,
    val protocol: String? = null,
    val padding: Boolean? = null,
)

/** VLESS outbound settings (`vless://`). */
data class VlessSettings(
    val uuid: String,
    /** e.g. `xtls-rprx-vision`; null when the server does not use a flow. */
    val flow: String? = null,
    val tls: TlsOptions = TlsOptions.None,
    val transport: TransportOptions = TransportOptions.Tcp,
    val mux: MuxOptions? = null,
) : ProfileSettings

/**
 * VMess outbound settings (`vmess://`).
 *
 * [security] is the cipher VMess negotiates — `auto` unless the link says
 * otherwise (`aes-128-gcm`, `chacha20-poly1305`, `none`). [alterId] is the
 * legacy MD5-auth counter: modern AEAD servers use 0, and a non-zero value
 * means the server is old.
 */
data class VmessSettings(
    val uuid: String,
    val security: String = "auto",
    val alterId: Int = 0,
    val tls: TlsOptions = TlsOptions.None,
    val transport: TransportOptions = TransportOptions.Tcp,
    val mux: MuxOptions? = null,
) : ProfileSettings

/** Trojan outbound settings (`trojan://`). */
data class TrojanSettings(
    val password: String,
    val tls: TlsOptions = TlsOptions.Plain,
    val transport: TransportOptions = TransportOptions.Tcp,
    val mux: MuxOptions? = null,
) : ProfileSettings

/** Shadowsocks outbound settings (`ss://`), including the 2022-blake3 ciphers. */
data class ShadowsocksSettings(
    val method: String,
    val password: String,
) : ProfileSettings

/**
 * Hysteria2 obfuscation. sing-box accepts only `salamander` and `gecko`, and
 * both require a password — a block with an empty password fails at connect.
 */
data class ObfsOptions(
    val type: String,
    val password: String,
)

/**
 * Hysteria2 outbound settings (`hysteria2://`, also `hy2://`).
 *
 * QUIC-based, so there is always TLS and never a stream transport: [tls] is
 * where the SNI, ALPN and "skip certificate verification" flag live.
 * [upMbps] / [downMbps] are the bandwidth hints its congestion control uses;
 * null means let the server decide.
 */
data class Hysteria2Settings(
    val password: String,
    val obfs: ObfsOptions? = null,
    val upMbps: Int? = null,
    val downMbps: Int? = null,
    val tls: TlsOptions = TlsOptions.Plain,
) : ProfileSettings

/** TUIC congestion control. sing-box accepts only these three. */
enum class TuicCongestionControl(val wireName: String) {
    CUBIC("cubic"),
    NEW_RENO("new_reno"),
    BBR("bbr"),
    ;

    companion object {
        fun fromWireName(name: String): TuicCongestionControl? =
            entries.firstOrNull { it.wireName.equals(name, ignoreCase = true) }
    }
}

/** How TUIC carries UDP. sing-box accepts only these two. */
enum class TuicUdpRelayMode(val wireName: String) {
    NATIVE("native"),
    QUIC("quic"),
    ;

    companion object {
        fun fromWireName(name: String): TuicUdpRelayMode? =
            entries.firstOrNull { it.wireName.equals(name, ignoreCase = true) }
    }
}

/**
 * TUIC outbound settings (`tuic://`).
 *
 * QUIC-based, like Hysteria2: no stream transport, no multiplex, TLS always
 * on. The two enums are here rather than plain strings because sing-box
 * constrains both fields — an unknown value fails the whole config and stops
 * the kernel — so the type is a better place to keep that out than a
 * validation call someone can forget.
 */
data class TuicSettings(
    val uuid: String,
    val password: String,
    val congestionControl: TuicCongestionControl? = null,
    val udpRelayMode: TuicUdpRelayMode? = null,
    val zeroRttHandshake: Boolean = false,
    val tls: TlsOptions = TlsOptions.Plain,
) : ProfileSettings
