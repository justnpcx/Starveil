package com.justnpcx.starveil.feature.editor

import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.ObfsOptions
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileSettings
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.ProfileValidation
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

/** A field the user can get wrong. Used to attach a message to the right input. */
enum class EditorField {
    SERVER,
    PORT,
    UUID,
    PASSWORD,
    REALITY_PUBLIC_KEY,
    METHOD,
    ALTER_ID,
    UP_MBPS,
    DOWN_MBPS,
    WG_PRIVATE_KEY,
    WG_ADDRESS,
    WG_PEER_PUBLIC_KEY,
    WG_PEER_ENDPOINT,
    WG_PEER_PORT,
    WG_MTU,
}

/** Why a field was rejected. The UI turns these into strings; the logic stays pure. */
enum class EditorError {
    /** A required field is empty. */
    REQUIRED,

    /** Not a number, or outside the allowed range. */
    INVALID_NUMBER,
}

/**
 * Everything the editor holds, flattened.
 *
 * One form for all seven protocols rather than seven forms: the fields a
 * protocol does not use are simply ignored when it is built, and the shared
 * blocks (basic / security / transport) are the same code for every protocol
 * that has them. Which blocks apply is answered by the `supports*` helpers
 * below, so the UI does not have to know the protocol table.
 *
 * Text fields are Strings, not typed values: a half-typed port is a legitimate
 * thing to hold in a form, and validating on save is what keeps `""` from
 * silently becoming port 0.
 */
data class EditorForm(
    val id: Long = 0L,
    val type: ProfileType,
    /** Kept so an edit does not lose the node's group. */
    val groupId: Long = 0L,
    val latencyMs: Long = -1L,

    // --- basic ---------------------------------------------------------
    val name: String = "",
    val server: String = "",
    val port: String = "",

    // --- security ------------------------------------------------------
    val security: SecurityMode = SecurityMode.NONE,
    val sni: String = "",
    val fingerprint: String = "",
    val allowInsecure: Boolean = false,
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val realityMlKem: Boolean = false,

    // --- transport -----------------------------------------------------
    val transport: TransportType = TransportType.TCP,
    val transportPath: String = "",
    val transportHost: String = "",
    val transportServiceName: String = "",
    /** XHTTP only; null means "let the core pick". */
    val transportMode: XhttpMode? = null,

    // --- protocol-specific ---------------------------------------------
    val uuid: String = "",
    val password: String = "",
    /** VLESS `flow`, e.g. `xtls-rprx-vision`. */
    val flow: String = "",
    val alterId: String = "0",
    val vmessSecurity: String = "auto",
    val ssMethod: String = "aes-256-gcm",
    val obfsType: String = "",
    val obfsPassword: String = "",
    val upMbps: String = "",
    val downMbps: String = "",
    val tuicCongestion: TuicCongestionControl? = null,
    val tuicUdpRelay: TuicUdpRelayMode? = null,

    // --- wireguard -----------------------------------------------------
    val wgPrivateKey: String = "",
    /** Comma-separated tunnel addresses, e.g. `10.0.0.2/32`. */
    val wgAddress: String = "",
    val wgPeerPublicKey: String = "",
    val wgPeerEndpoint: String = "",
    val wgPeerPort: String = "",
    val wgAllowedIps: String = "",
    val wgMtu: String = "",
) {

    /** VLESS / VMess / Trojan carry a stream transport; the QUIC ones do not. */
    val supportsTransport: Boolean
        get() = type == ProfileType.VLESS || type == ProfileType.VMESS || type == ProfileType.TROJAN

    /**
     * Hysteria2 and TUIC are QUIC, so TLS is not optional for them — hence
     * [allowsNoSecurity] rather than a single flag.
     */
    val supportsSecurity: Boolean
        get() = supportsTransport || type == ProfileType.HYSTERIA2 || type == ProfileType.TUIC

    val allowsNoSecurity: Boolean
        get() = supportsTransport

    /** Only these three can have a REALITY handshake. */
    val supportsReality: Boolean
        get() = supportsTransport

    /**
     * Hysteria2 and TUIC are QUIC: TLS is part of the protocol, not a choice.
     * A form that says otherwise must not be able to build one without it —
     * the server would simply refuse the connection.
     */
    val isQuic: Boolean
        get() = type == ProfileType.HYSTERIA2 || type == ProfileType.TUIC

    fun validate(): Map<EditorField, EditorError> {
        val errors = linkedMapOf<EditorField, EditorError>()

        if (!ProfileValidation.validHost(server)) errors[EditorField.SERVER] = EditorError.REQUIRED
        if (ProfileValidation.parsePort(port) == null) errors[EditorField.PORT] = EditorError.INVALID_NUMBER

        when (type) {
            ProfileType.VLESS, ProfileType.VMESS ->
                if (uuid.isBlank()) errors[EditorField.UUID] = EditorError.REQUIRED

            ProfileType.TUIC -> {
                if (uuid.isBlank()) errors[EditorField.UUID] = EditorError.REQUIRED
                if (password.isBlank()) errors[EditorField.PASSWORD] = EditorError.REQUIRED
            }

            ProfileType.TROJAN, ProfileType.HYSTERIA2 ->
                if (password.isBlank()) errors[EditorField.PASSWORD] = EditorError.REQUIRED

            ProfileType.SHADOWSOCKS -> {
                if (ssMethod.isBlank()) errors[EditorField.METHOD] = EditorError.REQUIRED
                if (password.isBlank()) errors[EditorField.PASSWORD] = EditorError.REQUIRED
            }

            ProfileType.WIREGUARD -> {
                if (wgPrivateKey.isBlank()) errors[EditorField.WG_PRIVATE_KEY] = EditorError.REQUIRED
                if (wgAddress.isBlank()) errors[EditorField.WG_ADDRESS] = EditorError.REQUIRED
                if (wgPeerPublicKey.isBlank()) errors[EditorField.WG_PEER_PUBLIC_KEY] = EditorError.REQUIRED
                if (wgPeerEndpoint.isBlank()) errors[EditorField.WG_PEER_ENDPOINT] = EditorError.REQUIRED
                if (ProfileValidation.parsePort(wgPeerPort) == null) {
                    errors[EditorField.WG_PEER_PORT] = EditorError.INVALID_NUMBER
                }
            }

            ProfileType.OTHER -> Unit
        }

        // REALITY without a public key cannot be built at all, so it is a field
        // error rather than a failure much later at connect time.
        if (security == SecurityMode.REALITY && realityPublicKey.isBlank()) {
            errors[EditorField.REALITY_PUBLIC_KEY] = EditorError.REQUIRED
        }

        // Optional numbers: blank is fine, garbage is not.
        if (alterId.isNotBlank() && alterId.trim().toIntOrNull() == null) {
            errors[EditorField.ALTER_ID] = EditorError.INVALID_NUMBER
        }
        for ((field, raw) in listOf(
            EditorField.UP_MBPS to upMbps,
            EditorField.DOWN_MBPS to downMbps,
            EditorField.WG_MTU to wgMtu,
        )) {
            if (raw.isNotBlank() && raw.trim().toIntOrNull() == null) {
                errors[field] = EditorError.INVALID_NUMBER
            }
        }

        return errors
    }

    /** The profile this form describes, or null while it is not yet valid. */
    fun toProfile(): Profile? {
        if (validate().isNotEmpty()) return null
        return Profile(
            id = id,
            name = name.ifBlank { server },
            type = type,
            server = server.trim(),
            port = ProfileValidation.parsePort(port) ?: return null,
            latencyMs = latencyMs,
            groupId = groupId,
            settings = toSettings(),
        )
    }

    private fun toSettings(): ProfileSettings = when (type) {
        ProfileType.VLESS -> VlessSettings(
            uuid = uuid.trim(),
            flow = flow.ifBlank { null },
            tls = tls(),
            transport = transport(),
        )

        ProfileType.VMESS -> VmessSettings(
            uuid = uuid.trim(),
            security = vmessSecurity.ifBlank { "auto" },
            alterId = alterId.trim().toIntOrNull() ?: 0,
            tls = tls(),
            transport = transport(),
        )

        ProfileType.TROJAN -> TrojanSettings(
            password = password,
            tls = tls(),
            transport = transport(),
        )

        ProfileType.SHADOWSOCKS -> ShadowsocksSettings(
            method = ssMethod.trim(),
            password = password,
        )

        ProfileType.HYSTERIA2 -> Hysteria2Settings(
            password = password,
            // Both obfs types require a password; a half-filled block would be
            // rejected by the server, so it is only emitted when complete.
            obfs = if (obfsType.isNotBlank() && obfsPassword.isNotBlank()) {
                ObfsOptions(type = obfsType, password = obfsPassword)
            } else {
                null
            },
            upMbps = upMbps.trim().toIntOrNull(),
            downMbps = downMbps.trim().toIntOrNull(),
            tls = tls(),
        )

        ProfileType.TUIC -> TuicSettings(
            uuid = uuid.trim(),
            password = password,
            congestionControl = tuicCongestion,
            udpRelayMode = tuicUdpRelay,
            tls = tls(),
        )

        ProfileType.WIREGUARD -> WireGuardSettings(
            privateKey = wgPrivateKey.trim(),
            address = wgAddress.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            peers = listOf(
                WireGuardPeer(
                    publicKey = wgPeerPublicKey.trim(),
                    endpointAddress = wgPeerEndpoint.trim(),
                    endpointPort = ProfileValidation.parsePort(wgPeerPort) ?: 0,
                    allowedIps = wgAllowedIps.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                ),
            ),
            mtu = wgMtu.trim().toIntOrNull(),
        )

        ProfileType.OTHER -> error("no settings for OTHER")
    }

    private fun tls(): TlsOptions = when (if (isQuic && security == SecurityMode.NONE) SecurityMode.TLS else security) {
        SecurityMode.NONE -> TlsOptions.None

        SecurityMode.TLS -> TlsOptions(
            security = SecurityMode.TLS,
            sni = sni.ifBlank { null },
            fingerprint = fingerprint.ifBlank { null },
            allowInsecure = allowInsecure,
        )

        SecurityMode.REALITY -> TlsOptions(
            security = SecurityMode.REALITY,
            sni = sni.ifBlank { null },
            fingerprint = fingerprint.ifBlank { null },
            allowInsecure = allowInsecure,
            reality = RealityOptions(
                publicKey = realityPublicKey.trim(),
                shortId = realityShortId.ifBlank { null },
                mlKem = realityMlKem,
            ),
        )
    }

    private fun transport(): TransportOptions = when {
        !supportsTransport || transport == TransportType.TCP -> TransportOptions.Tcp

        transport == TransportType.GRPC -> TransportOptions(
            type = TransportType.GRPC,
            serviceName = transportServiceName.ifBlank { null },
        )

        // ws / http / xhttp: path plus an optional host.
        else -> TransportOptions(
            type = transport,
            path = transportPath.ifBlank { null },
            host = transportHost.ifBlank { null },
            // Only xhttp has a mode; null leaves the core to choose.
            mode = transportMode.takeIf { transport == TransportType.XHTTP },
        )
    }

    companion object {
        /** Fills the form from an existing node, so editing round-trips. */
        fun from(profile: Profile): EditorForm {
            val base = EditorForm(
                id = profile.id,
                type = profile.type,
                groupId = profile.groupId,
                latencyMs = profile.latencyMs,
                name = profile.name,
                server = profile.server,
                port = profile.port.toString(),
            )
            val tls = profile.tls()
            val transport = profile.transport()
            return base.copy(
                // QUIC protocols have no "off": a node stored without TLS
                // still has to open as one that is on.
                security = if (profile.isQuic()) SecurityMode.TLS else tls?.security ?: SecurityMode.NONE,
                sni = tls?.sni.orEmpty(),
                fingerprint = tls?.fingerprint.orEmpty(),
                allowInsecure = tls?.allowInsecure ?: false,
                realityPublicKey = tls?.reality?.publicKey.orEmpty(),
                realityShortId = tls?.reality?.shortId.orEmpty(),
                realityMlKem = tls?.reality?.mlKem ?: false,
                transport = transport?.type ?: TransportType.TCP,
                transportPath = transport?.path.orEmpty(),
                transportHost = transport?.host.orEmpty(),
                transportServiceName = transport?.serviceName.orEmpty(),
                transportMode = transport?.mode,
            ).withSettings(profile.settings)
        }

        private fun EditorForm.withSettings(settings: ProfileSettings?): EditorForm = when (settings) {
            is VlessSettings -> copy(uuid = settings.uuid, flow = settings.flow.orEmpty())
            is VmessSettings -> copy(
                uuid = settings.uuid,
                vmessSecurity = settings.security,
                alterId = settings.alterId.toString(),
            )

            is TrojanSettings -> copy(password = settings.password)
            is ShadowsocksSettings -> copy(ssMethod = settings.method, password = settings.password)
            is Hysteria2Settings -> copy(
                password = settings.password,
                obfsType = settings.obfs?.type.orEmpty(),
                obfsPassword = settings.obfs?.password.orEmpty(),
                upMbps = settings.upMbps?.toString().orEmpty(),
                downMbps = settings.downMbps?.toString().orEmpty(),
            )

            is TuicSettings -> copy(
                uuid = settings.uuid,
                password = settings.password,
                tuicCongestion = settings.congestionControl,
                tuicUdpRelay = settings.udpRelayMode,
            )

            is WireGuardSettings -> {
                val peer = settings.peers.firstOrNull()
                copy(
                    wgPrivateKey = settings.privateKey,
                    wgAddress = settings.address.joinToString(", "),
                    wgPeerPublicKey = peer?.publicKey.orEmpty(),
                    wgPeerEndpoint = peer?.endpointAddress.orEmpty(),
                    wgPeerPort = peer?.endpointPort?.takeIf { it > 0 }?.toString().orEmpty(),
                    wgAllowedIps = peer?.allowedIps?.joinToString(", ").orEmpty(),
                    wgMtu = settings.mtu?.toString().orEmpty(),
                )
            }

            null -> this
        }

        private fun Profile.tls(): TlsOptions? = when (val s = settings) {
            is VlessSettings -> s.tls
            is VmessSettings -> s.tls
            is TrojanSettings -> s.tls
            is Hysteria2Settings -> s.tls
            is TuicSettings -> s.tls
            else -> null
        }

        private fun Profile.transport(): TransportOptions? = when (val s = settings) {
            is VlessSettings -> s.transport
            is VmessSettings -> s.transport
            is TrojanSettings -> s.transport
            else -> null
        }

        private fun Profile.isQuic(): Boolean =
            type == ProfileType.HYSTERIA2 || type == ProfileType.TUIC
    }
}
