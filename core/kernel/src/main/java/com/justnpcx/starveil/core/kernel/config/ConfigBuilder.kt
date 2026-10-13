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

    private fun render(outbounds: List<Map<String, Any?>>, finalTag: String): String {
        val config = linkedMapOf<String, Any?>(
            "log" to linkedMapOf("level" to "warn", "timestamp" to true),
            "dns" to linkedMapOf(
                "servers" to listOf(linkedMapOf<String, Any?>("address" to "local")),
            ),
            "inbounds" to emptyList<Any?>(),
            "outbounds" to outbounds,
            "route" to linkedMapOf<String, Any?>(
                "rules" to emptyList<Any?>(),
                "final" to finalTag,
            ),
        )
        return Json.write(config)
    }

    companion object {
        const val PROXY_TAG = "proxy"
        const val AUTO_TAG = "auto"
        const val DIRECT_TAG = "direct"
        const val BLOCK_TAG = "block"
    }
}

/** A config could not be built from the given profile. Carries no credentials. */
class ConfigBuildException(message: String) : IllegalArgumentException(message)
