package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.json.Json

/**
 * Assembles a complete sing-box JSON config from one or more [Profile]s.
 *
 * Pure and deterministic: same input -> same output, no IO, no clock, no
 * randomness. Safe to call off the main thread and trivial to unit test.
 *
 * Outbound tags are assigned by the builder (`proxy` for a single profile,
 * `proxy-<index>` plus an `auto` urltest group for many) so a
 * subscription-supplied name can never shadow the built-in `direct` / `block`
 * outbounds.
 *
 * @throws ConfigBuildException when a profile's protocol has no descriptor or
 *   its settings are missing/mismatched. The message never contains credentials.
 */
class ConfigBuilder(private val registry: ProtocolRegistry) {

    /** Single-profile config: one `proxy` outbound, route final = `proxy`. */
    fun build(profile: Profile): String {
        val outbounds = listOf(
            outbound(profile, PROXY_TAG),
            directOutbound(),
            blockOutbound(),
        )
        return render(outbounds = outbounds, finalTag = PROXY_TAG)
    }

    /**
     * Multi-profile config: one outbound per profile plus an `auto` urltest
     * group, which becomes the route's final outbound. Used for subscription
     * imports and for the node-switch flow.
     */
    fun build(profiles: List<Profile>): String {
        require(profiles.isNotEmpty()) { "profiles must not be empty" }
        val tags = profiles.indices.map { "proxy-$it" }
        val outbounds = buildList {
            profiles.forEachIndexed { index, profile -> add(outbound(profile, tags[index])) }
            add(
                linkedMapOf<String, Any?>(
                    "type" to "urltest",
                    "tag" to AUTO_TAG,
                    "outbounds" to tags,
                    "url" to "http://cp.cloudflare.com/",
                    "interval" to "3m",
                    "tolerance" to 50,
                ),
            )
            add(directOutbound())
            add(blockOutbound())
        }
        return render(outbounds = outbounds, finalTag = AUTO_TAG)
    }

    private fun outbound(profile: Profile, tag: String): Map<String, Any?> {
        val descriptor = registry.get(profile.type)
            ?: throw ConfigBuildException("No protocol descriptor registered for ${profile.type}")
        val fragment = try {
            descriptor.configBuilder(profile)
        } catch (e: IllegalStateException) {
            throw ConfigBuildException("Cannot build ${profile.type} outbound: ${e.message}")
        }
        return LinkedHashMap<String, Any?>(fragment).apply { put("tag", tag) }
    }

    private fun directOutbound() = linkedMapOf<String, Any?>("type" to "direct", "tag" to DIRECT_TAG)
    private fun blockOutbound() = linkedMapOf<String, Any?>("type" to "block", "tag" to BLOCK_TAG)

    /**
     * The tun inbound. Mandatory on Android: the kernel declares the interface
     * here and then calls `PlatformInterface.openTun` for the file descriptor
     * that [com.justnpcx.starveil.core.kernel.vpn.StarveilVpnService]
     * established. With no inbound the kernel has nothing to attach the tun to
     * and never asks for a descriptor at all.
     */
    private fun tunInbound() = linkedMapOf<String, Any?>(
        "type" to "tun",
        "tag" to TUN_TAG,
        "address" to listOf("172.19.0.1/30", "fdfe:dcba:9876::1/126"),
        "mtu" to 9000,
        // hijack: sing-box answers DNS itself instead of letting queries reach
        // the tun and be dropped. Without it an Android app resolves nothing
        // and reports "cannot use the network".
        "dns_mode" to "hijack",
        "auto_route" to true,
        "strict_route" to false,
        // gvisor, not mixed: the mixed stack sends UDP through the system
        // stack, which needs privileges Android does not grant an app.
        "stack" to "gvisor",
    )

    private fun render(outbounds: List<Map<String, Any?>>, finalTag: String): String {
        val config = linkedMapOf<String, Any?>(
            "log" to linkedMapOf("level" to "warn", "timestamp" to true),
            "dns" to linkedMapOf(
                // New DNS server format. The legacy {"address": "local"} shape
                // was deprecated in sing-box 1.12 and REMOVED in 1.14, where it
                // fails config decode with an explicit migration message.
                "servers" to listOf(
                    linkedMapOf<String, Any?>("type" to "local", "tag" to LOCAL_DNS_TAG),
                ),
            ),
            "inbounds" to listOf(tunInbound()),
            "outbounds" to outbounds,
            "route" to linkedMapOf<String, Any?>(
                "rules" to emptyList<Any?>(),
                "final" to finalTag,
                // Off on purpose. With it on, this fork's dialer takes the
                // platform-interfaces path and binds every outbound socket to
                // the interface — device log: "dial wlan0 (37)" — and that dial
                // times out for a node that another client on the same phone
                // reaches fine. Off, the dial is a plain one; our own package
                // is excluded from the tunnel, so its sockets still go direct.
                // The cost is that PlatformInterface.AutoDetectInterfaceControl
                // (VpnService.protect) is no longer called — which the
                // exclusion already covers.
                "auto_detect_interface" to false,
            ),
        )
        return Json.write(config)
    }

    companion object {
        const val PROXY_TAG = "proxy"
        const val AUTO_TAG = "auto"
        const val DIRECT_TAG = "direct"
        const val BLOCK_TAG = "block"
        const val TUN_TAG = "tun-in"
        const val LOCAL_DNS_TAG = "local"
    }
}

/** A config could not be built from the given profile. Carries no credentials. */
class ConfigBuildException(message: String) : IllegalArgumentException(message)
