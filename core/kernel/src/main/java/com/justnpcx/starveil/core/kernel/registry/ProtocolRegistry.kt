package com.justnpcx.starveil.core.kernel.registry

import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType

/**
 * Everything the app needs to know about one protocol, in one place.
 *
 * Adding a protocol = registering one [ProtocolDescriptor]. No `when`
 * branches on [ProfileType] anywhere else in the app — that was the
 * enum-dispatch trap this registry replaces.
 *
 * - [linkParser]: parse a share link (`vless://…`) into a [Profile], or
 *   null if the link is not this protocol's. Pure function, no IO.
 * - [configBuilder]: build this protocol's sing-box outbound fragment
 *   from a [Profile]. Pure function; the real ConfigBuilder consumes
 *   these fragments. Must never include credentials in logs.
 * - [editorRoute]: the Compose navigation route of this protocol's edit
 *   screen. UI resolves it; `:core:kernel` never imports UI code.
 */
data class ProtocolDescriptor(
    val type: ProfileType,
    val displayName: String,
    val defaultPort: Int,
    /** URI schemes this protocol claims, without "://" (e.g. "vless"). */
    val schemes: List<String>,
    val linkParser: (String) -> Profile?,
    val configBuilder: (Profile) -> Map<String, Any?>,
    val editorRoute: String,
)

/**
 * Protocol registry: the single lookup for protocol capabilities.
 *
 * Assembled once in DI ([com.justnpcx.starveil.di.RegistryModule]) from
 * [default]. Extend without touching existing code via [withExtra].
 */
class ProtocolRegistry(private val descriptors: Map<ProfileType, ProtocolDescriptor>) {

    init {
        require(descriptors.isNotEmpty()) { "ProtocolRegistry must contain at least one descriptor" }
    }

    fun get(type: ProfileType): ProtocolDescriptor? = descriptors[type]

    fun all(): List<ProtocolDescriptor> = descriptors.values.toList()

    /** Match a URI scheme ("vless" in "vless://…") to its descriptor. */
    fun fromScheme(scheme: String): ProtocolDescriptor? =
        descriptors.values.firstOrNull { descriptor ->
            descriptor.schemes.any { it.equals(scheme, ignoreCase = true) }
        }

    /** Return a new registry with one extra descriptor; this one is unchanged. */
    fun withExtra(descriptor: ProtocolDescriptor): ProtocolRegistry =
        ProtocolRegistry(descriptors + (descriptor.type to descriptor))

    companion object {
        /**
         * Built-in protocols. Parsers / config builders are stubs until
         * Phase 2 lands the real per-protocol implementations — each stub
         * is marked with a TODO so a half-wired protocol is visible.
         */
        fun default(): ProtocolRegistry = ProtocolRegistry(
            listOf(
                stub(
                    type = ProfileType.VLESS,
                    displayName = "VLESS",
                    defaultPort = 443,
                    schemes = listOf("vless"),
                ),
                stub(
                    type = ProfileType.VMESS,
                    displayName = "VMess",
                    defaultPort = 443,
                    schemes = listOf("vmess"),
                ),
                stub(
                    type = ProfileType.TROJAN,
                    displayName = "Trojan",
                    defaultPort = 443,
                    schemes = listOf("trojan"),
                ),
                stub(
                    type = ProfileType.SHADOWSOCKS,
                    displayName = "Shadowsocks",
                    defaultPort = 8388,
                    schemes = listOf("ss"),
                ),
                stub(
                    type = ProfileType.HYSTERIA2,
                    displayName = "Hysteria2",
                    defaultPort = 443,
                    schemes = listOf("hysteria2", "hy2"),
                ),
                stub(
                    type = ProfileType.TUIC,
                    displayName = "TUIC",
                    defaultPort = 443,
                    schemes = listOf("tuic"),
                ),
                stub(
                    type = ProfileType.WIREGUARD,
                    displayName = "WireGuard",
                    defaultPort = 51820,
                    schemes = listOf("wireguard"),
                ),
            ).associateBy { it.type },
        )

        private fun stub(
            type: ProfileType,
            displayName: String,
            defaultPort: Int,
            schemes: List<String>,
        ): ProtocolDescriptor = ProtocolDescriptor(
            type = type,
            displayName = displayName,
            defaultPort = defaultPort,
            schemes = schemes,
            // TODO (Phase 2): real link parser for $displayName.
            linkParser = { null },
            // TODO (Phase 2): real sing-box outbound fragment for $displayName.
            configBuilder = { emptyMap() },
            editorRoute = "editor/${type.name.lowercase()}",
        )
    }
}
