package com.justnpcx.starveil.core.kernel.registry

import com.justnpcx.starveil.core.kernel.protocol.StarveilProtocols
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType

/**
 * Where a protocol's config fragment belongs in the sing-box config.
 *
 * Almost everything is an outbound. WireGuard is an *endpoint*: sing-box 1.13
 * removed its outbound form, and emitting it under `outbounds` fails config
 * construction with "use WireGuard endpoint instead" — which stops the kernel.
 */
enum class ConfigPlacement { OUTBOUND, ENDPOINT }

/**
 * Everything the app needs to know about one protocol, in one place.
 *
 * Adding a protocol = registering one [ProtocolDescriptor]. No `when`
 * branches on [ProfileType] anywhere else in the app — that was the
 * enum-dispatch trap this registry replaces.
 *
 * - [linkParser]: parse a share link (`vless://…`) into a [Profile], or
 *   null if the link is not this protocol's. Pure function, no IO.
 * - [configBuilder]: build this protocol's sing-box fragment from a
 *   [Profile]. Pure function; the real ConfigBuilder consumes these
 *   fragments. Must never include credentials in logs.
 */
data class ProtocolDescriptor(
    val type: ProfileType,
    val displayName: String,
    val defaultPort: Int,
    /** URI schemes this protocol claims, without "://" (e.g. "vless"). */
    val schemes: List<String>,
    val linkParser: (String) -> Profile?,
    /**
     * The inverse of [linkParser]: this protocol's share link for a profile, or
     * null when the profile is not something this protocol can express.
     *
     * Registered here rather than dispatched on in the writer, so adding a
     * protocol means registering both halves and neither can be forgotten.
     * Whatever it emits, [linkParser] must read back into an equal profile —
     * that round trip is the entire contract, and the tests pin it per protocol.
     */
    val linkWriter: (Profile) -> String?,
    val configBuilder: (Profile) -> Map<String, Any?>,
    /**
     * Navigation route of this protocol's editor (e.g. `editor/vless`).
     *
     * Declared here so adding a protocol does not mean touching navigation: the
     * graph is built from the registry. (This field existed before and was
     * deleted as unread — at the time there was no editor for it to point at.)
     */
    val editorRoute: String,
    /**
     * True when [linkParser] should also be tried on text that carries no
     * scheme — a WireGuard `.conf`, for instance. Scheme-ful links never reach
     * this, so it is only the fallback path.
     */
    val sniff: (String) -> Boolean = { false },
    /** Outbound by default; only WireGuard is an endpoint. */
    val placement: ConfigPlacement = ConfigPlacement.OUTBOUND,
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

    /**
     * This profile as a share link, or null when no registered protocol can
     * express it.
     *
     * A blank result is treated as "cannot" rather than passed on: an empty
     * link is not something anything can import, and handing it to the UI would
     * put an empty QR code on screen.
     */
    fun writeLink(profile: Profile): String? =
        get(profile.type)?.linkWriter?.invoke(profile)?.takeIf { it.isNotBlank() }

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
         * Built-in protocols. Every one is real now, so the registry is just
         * the descriptors' own list — there are no stubs left to keep visible.
         */
        fun default(): ProtocolRegistry =
            ProtocolRegistry(StarveilProtocols.all.associateBy { it.type })
    }
}
