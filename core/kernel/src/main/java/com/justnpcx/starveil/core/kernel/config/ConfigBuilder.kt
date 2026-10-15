package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.kernel.registry.ConfigPlacement
import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
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
    fun build(profile: Profile, rules: List<Rule> = emptyList()): String {
        val proxy = fragment(profile, PROXY_TAG)
        // WireGuard is an endpoint, so its fragment goes in a different
        // top-level section — and the route still points at its tag, because
        // the outbound manager resolves an endpoint tag too.
        val isEndpoint = placementOf(profile) == ConfigPlacement.ENDPOINT
        return render(
            outbounds = buildList {
                if (!isEndpoint) add(proxy)
                add(directOutbound())
                add(blockOutbound())
            },
            endpoints = if (isEndpoint) listOf(proxy) else emptyList(),
            finalTag = PROXY_TAG,
            rules = rules,
        )
    }

    /**
     * Multi-profile config: one outbound per profile plus an `auto` urltest
     * group, which becomes the route's final outbound. Used for subscription
     * imports and for the node-switch flow.
     */
    fun build(profiles: List<Profile>, rules: List<Rule> = emptyList()): String {
        require(profiles.isNotEmpty()) { "profiles must not be empty" }
        val tags = profiles.indices.map { "proxy-$it" }
        val fragments = profiles.mapIndexed { index, profile -> fragment(profile, tags[index]) }
        // One placement lookup per profile: this runs for every node of a
        // subscription, and the builder is on a latency-sensitive path.
        val placements = profiles.map { placementOf(it) }
        val endpoints = profiles.indices
            .filter { placements[it] == ConfigPlacement.ENDPOINT }
            .map { fragments[it] }

        val outbounds = buildList {
            profiles.indices
                .filter { placements[it] == ConfigPlacement.OUTBOUND }
                .forEach { add(fragments[it]) }
            add(
                linkedMapOf<String, Any?>(
                    "type" to "urltest",
                    "tag" to AUTO_TAG,
                    // Endpoint tags are listed too: the outbound manager falls
                    // back to the endpoint manager when a tag is not an
                    // outbound, so the group can carry both kinds.
                    "outbounds" to tags,
                    "url" to "http://cp.cloudflare.com/",
                    "interval" to "3m",
                    "tolerance" to 50,
                ),
            )
            add(directOutbound())
            add(blockOutbound())
        }
        return render(
            outbounds = outbounds,
            endpoints = endpoints,
            finalTag = AUTO_TAG,
            rules = rules,
        )
    }

    /**
     * A config with no proxy at all: the tunnel comes up and every connection
     * is rejected, because the route's `final` is the built-in `block`
     * outbound. This is what the Kill Switch runs — traffic stays inside a
     * tunnel that answers nothing, rather than escaping to the network.
     *
     * It carries no node, so it never touches the user's credentials.
     */
    fun buildBlocking(): String =
        render(
            outbounds = listOf(blockOutbound()),
            endpoints = emptyList(),
            finalTag = BLOCK_TAG,
            rules = emptyList(),
        )

    /**
     * Enabled rules, in the order the user arranged them — the first match
     * wins, so this order is the routing decision.
     *
     * The action uses the union form sing-box 1.14 documents: `route` carries
     * the outbound tag, `direct` and `reject` carry nothing. The older
     * `{"outbound": "direct"}` shape is deprecated, and `reject` has no
     * outbound at all, so the union is the only shape that covers all three.
     */
    private fun routeRules(rules: List<Rule>, proxyTag: String): List<Map<String, Any?>> =
        rules.filter { it.enabled }.map { rule ->
            val matcher = linkedMapOf<String, Any?>(
                rule.matcher.kind.wireName to listOf(rule.matcher.value),
            )
            val action = when (rule.action) {
                RuleAction.PROXY -> linkedMapOf<String, Any?>("action" to "route", "outbound" to proxyTag)
                RuleAction.DIRECT -> linkedMapOf<String, Any?>("action" to "direct")
                RuleAction.BLOCK -> linkedMapOf<String, Any?>("action" to "reject")
            }
            matcher + action
        }

    /** Where this profile's fragment belongs; unknown types default to outbound. */
    private fun placementOf(profile: Profile): ConfigPlacement =
        registry.get(profile.type)?.placement ?: ConfigPlacement.OUTBOUND

    private fun fragment(profile: Profile, tag: String): Map<String, Any?> {
        val descriptor = registry.get(profile.type)
            ?: throw ConfigBuildException("No protocol descriptor registered for ${profile.type}")
        val built = try {
            descriptor.configBuilder(profile)
        } catch (e: IllegalStateException) {
            throw ConfigBuildException("Cannot build ${profile.type} fragment: ${e.message}")
        }
        return LinkedHashMap<String, Any?>(built).apply { put("tag", tag) }
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

    private fun render(
        outbounds: List<Map<String, Any?>>,
        endpoints: List<Map<String, Any?>>,
        finalTag: String,
        rules: List<Rule>,
    ): String {
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
        )
        // WireGuard's endpoint goes here, not in `outbounds`: sing-box 1.13
        // removed the outbound form and fails config construction on it.
        // Omitted when empty, so a config with no endpoint is unchanged.
        if (endpoints.isNotEmpty()) {
            config["endpoints"] = endpoints
        }
        config["route"] = linkedMapOf<String, Any?>(
            "rules" to routeRules(rules, finalTag),
            "final" to finalTag,
            // On: this is the platform path, and it is the one we want.
            // The dialer installs the bind/protect control, which reaches
            // PlatformInterface.AutoDetectInterfaceControl, and it takes the
            // platform-interfaces path (this fork hardcodes
            // UsePlatformNetworkInterfaces() to true), which is what
            // PlatformInterface.GetInterfaces() feeds. Turning it off was
            // only an A/B and made no difference to the dial.
            "auto_detect_interface" to true,
        )
        // Present, though empty. sing-box only builds its traffic manager —
        // and therefore only reports uplink/downlink to the CommandClient —
        // when `experimental.clash_api` is set (box.go: `needClashAPI`).
        // Without it every StatusMessage carries zero, so the dashboard's
        // rates and totals sit at 0 B/s forever even while traffic flows.
        // An empty controller opens no HTTP listener: the server is created
        // but never bound.
        config["experimental"] = linkedMapOf<String, Any?>(
            "clash_api" to emptyMap<String, Any?>(),
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
