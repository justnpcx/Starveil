package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.kernel.registry.ConfigPlacement
import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.MainlandRuleSets
import com.justnpcx.starveil.core.model.InstalledRulePack
import com.justnpcx.starveil.core.model.LocalApiConfig
import com.justnpcx.starveil.core.model.PerAppOutbound
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RulePackAction
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
    fun build(
        profile: Profile,
        rules: List<Rule> = emptyList(),
        mainland: MainlandRuleSets? = null,
        perApp: List<PerAppOutbound> = emptyList(),
        preset: RoutePreset = RoutePreset.GLOBAL_PROXY,
        adsPath: String? = null,
        rulePacks: List<InstalledRulePack> = emptyList(),
        localApi: LocalApiConfig? = null,
        localSubnets: List<String> = emptyList(),
        subscriptionProxyPort: Int? = null,
    ): String {
        val proxy = fragment(profile, PROXY_TAG)
        // WireGuard is an endpoint, so its fragment goes in a different
        // top-level section — and the route still points at its tag, because
        // the outbound manager resolves an endpoint tag too.
        val isEndpoint = placementOf(profile) == ConfigPlacement.ENDPOINT
        val perAppEmission = emitPerApp(perApp)
        return render(
            outbounds = buildList {
                if (!isEndpoint) add(proxy)
                addAll(perAppEmission.outbounds)
                add(directOutbound())
                add(blockOutbound())
            },
            endpoints = (if (isEndpoint) listOf(proxy) else emptyList()) + perAppEmission.endpoints,
            // "Direct everything" still builds the proxy outbound: the node is
            // what the user configured, and switching the policy back must not
            // require reconnecting to something else.
            finalTag = if (preset == RoutePreset.GLOBAL_DIRECT) DIRECT_TAG else PROXY_TAG,
            rules = rules,
            mainland = mainland,
            perAppRules = perAppEmission.rules,
            preset = preset,
            adsPath = adsPath,
            rulePacks = rulePacks,
            proxyTag = PROXY_TAG,
            localApi = localApi,
            localSubnets = localSubnets,
            subscriptionProxyPort = subscriptionProxyPort,
        )
    }

    /**
     * Group config: one outbound per profile, plus a group outbound that
     * becomes the route's final target, so the kernel chooses between the
     * nodes instead of being pinned to one.
     *
     * [mode] picks the group type:
     *  - [GroupMode.URL_TEST] — the kernel measures the members and keeps using
     *    the fastest.
     *  - [GroupMode.SELECTOR] — the group stays on one member (the first) until
     *    something changes the pick.
     *  - [GroupMode.MANUAL] is rejected: a manual group is a plain folder, not
     *    something you connect *through*, so there is no group outbound to
     *    build. Callers pick a member and use the single-profile [build].
     */
    fun build(
        profiles: List<Profile>,
        rules: List<Rule> = emptyList(),
        mode: GroupMode = GroupMode.URL_TEST,
        mainland: MainlandRuleSets? = null,
        perApp: List<PerAppOutbound> = emptyList(),
        preset: RoutePreset = RoutePreset.GLOBAL_PROXY,
        adsPath: String? = null,
        rulePacks: List<InstalledRulePack> = emptyList(),
        localApi: LocalApiConfig? = null,
        localSubnets: List<String> = emptyList(),
        subscriptionProxyPort: Int? = null,
    ): String {
        require(profiles.isNotEmpty()) { "profiles must not be empty" }
        require(mode != GroupMode.MANUAL) {
            "a MANUAL group is a folder; connect to one of its nodes instead"
        }
        val tags = profiles.indices.map { "proxy-$it" }
        val fragments = profiles.mapIndexed { index, profile -> fragment(profile, tags[index]) }
        // One placement lookup per profile: this runs for every node of a
        // subscription, and the builder is on a latency-sensitive path.
        val placements = profiles.map { placementOf(it) }
        val endpoints = profiles.indices
            .filter { placements[it] == ConfigPlacement.ENDPOINT }
            .map { fragments[it] }
        val perAppEmission = emitPerApp(perApp)

        val outbounds = buildList {
            profiles.indices
                .filter { placements[it] == ConfigPlacement.OUTBOUND }
                .forEach { add(fragments[it]) }
            addAll(perAppEmission.outbounds)
            add(
                when (mode) {
                    GroupMode.URL_TEST -> linkedMapOf<String, Any?>(
                        "type" to "urltest",
                        "tag" to AUTO_TAG,
                        // Endpoint tags are listed too: the outbound manager
                        // falls back to the endpoint manager when a tag is not
                        // an outbound, so the group can carry both kinds.
                        "outbounds" to tags,
                        "url" to "http://cp.cloudflare.com/",
                        "interval" to "3m",
                        "tolerance" to 50,
                    )

                    GroupMode.SELECTOR -> linkedMapOf<String, Any?>(
                        "type" to "selector",
                        "tag" to AUTO_TAG,
                        "outbounds" to tags,
                        // A selector has to start somewhere; without a control
                        // channel to change it later, this is the pick.
                        "default" to tags.first(),
                    )

                    GroupMode.MANUAL -> error("rejected above")
                },
            )
            add(directOutbound())
            add(blockOutbound())
        }
        return render(
            outbounds = outbounds,
            endpoints = endpoints + perAppEmission.endpoints,
            finalTag = if (preset == RoutePreset.GLOBAL_DIRECT) DIRECT_TAG else AUTO_TAG,
            rules = rules,
            mainland = mainland,
            perAppRules = perAppEmission.rules,
            preset = preset,
            adsPath = adsPath,
            rulePacks = rulePacks,
            proxyTag = AUTO_TAG,
            localApi = localApi,
            localSubnets = localSubnets,
            subscriptionProxyPort = subscriptionProxyPort,
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
            // A cache file of its own. libbox keeps the previous instance's
            // cache database open for as long as that instance lives, and the
            // swap starts the new instance before the old one's file is
            // released — with one shared path the new open waits on the lock
            // and fails with `initialize cache-file: timeout`. Two files, one
            // per policy, is bounded and removes the contention entirely.
            cacheFileName = "cache-block.db",
        )

    /**
     * Enabled rules, in the order the user arranged them — the first match
     * wins, so this order is the routing decision.
     *
     * Both routing actions are emitted as `route`, with the outbound named.
     * `direct` is not one of them: it is a *DNS* rule action that sing-box
     * happens to accept in a route rule and then never acts on. The router
     * matches such a rule, logs `=> direct`, does not select it, and the
     * connection falls through to `final` — so a "direct" rule silently
     * proxied everything it was meant to keep at home. `route` + `outbound`
     * is the form the route rule action table actually defines. Blocking
     * stays `reject`, which is real and carries no outbound.
     */
    internal fun routeRules(
        rules: List<Rule>,
        finalTag: String,
        proxyTag: String,
        mainland: MainlandRuleSets?,
        perAppRules: List<Map<String, Any?>>,
        preset: RoutePreset,
        adsPath: String?,
        rulePacks: List<InstalledRulePack>,
        localSubnets: List<String>,
    ): List<Map<String, Any?>> {
        // Sniffing first, and this is not a preference: every domain rule in
        // this config is unreachable without it. A connection arriving at the
        // tun carries a destination *address* — the app resolved the name
        // itself, or the hijacked DNS did it on the app's behalf — so a
        // `rule_set` of geosite type, or a user's `domain` matcher, has
        // nothing to compare against and never matches. That is exactly the
        // failure the ad list showed: `doubleclick.net` went out through the
        // proxy with the reject rule sitting above it in the same list.
        //
        // `sniff` is a non-final action: it reads the first bytes of the
        // connection, writes the name it finds into the metadata, and lets
        // matching continue. Only the three protocols that carry a name are
        // asked for — TLS ClientHello, HTTP request line, QUIC Initial — so a
        // plain SSH or WireGuard connection is not held waiting for a name it
        // will never send. The default timeout (300 ms) stays: it is the
        // upstream default, and a connection whose first packet is slower
        // than that simply proceeds unmatched, which is what happened before
        // this rule existed.
        val sniff = listOf(
            linkedMapOf<String, Any?>(
                "action" to "sniff",
                "sniffer" to listOf("tls", "http", "quic"),
            ),
        )
        // Per-app next, and that is a deliberate precedence: someone who says
        // "this app goes this way" means it more specifically than any domain
        // rule, and a bank app that reaches a rule-matched domain must still
        // take the route it was given. `package_name` is matched from the
        // platform's connection-owner lookup (see
        // StarveilPlatformInterface.findConnectionOwner); with no owner data
        // the rule simply never matches, which is why that lookup has to be
        // real for this to do anything.
        val perApp = perAppRules
        val userRules = rules.filter { it.enabled }.map { rule ->
            val matcher = linkedMapOf<String, Any?>(
                rule.matcher.kind.wireName to listOf(rule.matcher.value),
            )
            val action = when (rule.action) {
                RuleAction.PROXY -> linkedMapOf<String, Any?>("action" to "route", "outbound" to proxyTag)
                RuleAction.DIRECT ->
                    linkedMapOf<String, Any?>("action" to "route", "outbound" to DIRECT_TAG)
                RuleAction.BLOCK -> linkedMapOf<String, Any?>("action" to "reject")
            }
            matcher + action
        }
        // Ads first: blocking them is a policy the user turned on explicitly,
        // and a later rule that would route the request somewhere must not
        // quietly undo it. Only the per-app direct exception could argue
        // otherwise, and an ad request is not the reason anyone whitelists an
        // app.
        val ads = if (adsPath == null) {
            emptyList()
        } else {
            listOf(linkedMapOf<String, Any?>("rule_set" to ADS_TAG, "action" to "reject"))
        }
        // Subscribed packs, after per-app so an app exception still wins, and
        // before the user's own rules so a domain someone whitelisted by hand
        // is not overruled by a pack they subscribed to later.
        //
        // A pack that routes to the proxy names [proxyTag], not the route's
        // final: under "global direct" the final is `direct`, and a streaming
        // pack that sent its domains direct would be the opposite of what the
        // user subscribed to.
        val packs = rulePacks.map { installed ->
            when (installed.pack.action) {
                RulePackAction.REJECT ->
                    linkedMapOf<String, Any?>("rule_set" to installed.pack.tag, "action" to "reject")

                RulePackAction.PROXY -> linkedMapOf<String, Any?>(
                    "rule_set" to installed.pack.tag,
                    "action" to "route",
                    "outbound" to proxyTag,
                )
            }
        }
        // What is on the same link as this phone goes direct, and nothing else
        // does. That distinction is the whole point: a private address is not
        // one thing. `192.168.50.10` is the NAS one hop away when the phone is
        // at home, and the same address is unreachable from a hotel — where
        // the right answer is the node, because the node is the one standing
        // in that house.
        //
        // So the rule is built from the interface's own addresses rather than
        // from a list of private ranges. A network change rebuilds the tunnel,
        // which is the only way a static config can follow the phone.
        //
        // Loopback, multicast and link-local are direct regardless: no proxy
        // carries them, and putting them in the tunnel breaks discovery
        // without making anything reachable.
        //
        // After per-app, before the user's rules: an app-level exception is
        // more specific than an address, and everything below this line is
        // about reaching the internet.
        val lan = listOf(
            linkedMapOf<String, Any?>(
                "ip_cidr" to (ALWAYS_DIRECT_CIDRS + localSubnets).distinct(),
                "action" to "route",
                "outbound" to DIRECT_TAG,
            ),
        )
        // The preset, not the files, decides whether domestic traffic is split
        // out: a missing rule set means the preset silently degrades, and the
        // rules must degrade with it.
        if (preset != RoutePreset.BYPASS_MAINLAND || mainland == null) {
            return sniff + ads + perApp + packs + lan + userRules
        }
        // After the user's own rules and before `final`: the user's intent wins
        // over the preset, which is the only order that lets someone carve an
        // exception out of it. geosite-cn is a domain match, so a domestic
        // name is sent direct before any DNS answer is needed; geoip-cn then
        // catches the addresses that were already resolved elsewhere.
        return sniff + ads + perApp + packs + lan + userRules + listOf(
            linkedMapOf<String, Any?>(
                "rule_set" to GEOSITE_CN_TAG,
                "action" to "route",
                "outbound" to DIRECT_TAG,
            ),
            linkedMapOf<String, Any?>(
                "rule_set" to GEOIP_CN_TAG,
                "action" to "route",
                "outbound" to DIRECT_TAG,
            ),
        )
    }

    /**
     * Turns the per-app targets into outbounds and the rules that select them.
     *
     * One outbound per target, not per app: two apps pointing at the same node
     * share a connection pool instead of dialing it twice. The tags are
     * `per-app-<n>`, which cannot collide with `proxy`, `proxy-<n>`, `auto`,
     * `direct` or `block`.
     *
     * A target that is a WireGuard endpoint goes in `endpoints`, same as the
     * main target — sing-box 1.13 removed the outbound form and fails config
     * construction on it.
     */
    private fun emitPerApp(perApp: List<PerAppOutbound>): PerAppEmission {
        if (perApp.isEmpty()) return PerAppEmission()
        val outbounds = mutableListOf<Map<String, Any?>>()
        val endpoints = mutableListOf<Map<String, Any?>>()
        val rules = mutableListOf<Map<String, Any?>>()
        perApp.forEachIndexed { index, entry ->
            val outboundTag = when (entry) {
                is PerAppOutbound.Direct -> DIRECT_TAG
                is PerAppOutbound.Node -> {
                    val nodeTag = "$PER_APP_TAG_PREFIX$index"
                    val nodeFragment = fragment(entry.profile, nodeTag)
                    if (placementOf(entry.profile) == ConfigPlacement.ENDPOINT) {
                        endpoints += nodeFragment
                    } else {
                        outbounds += nodeFragment
                    }
                    nodeTag
                }
            }
            rules += linkedMapOf<String, Any?>(
                "package_name" to entry.packages.sorted(),
                "action" to "route",
                "outbound" to outboundTag,
            )
        }
        return PerAppEmission(outbounds, endpoints, rules)
    }

    private class PerAppEmission(
        val outbounds: List<Map<String, Any?>> = emptyList(),
        val endpoints: List<Map<String, Any?>> = emptyList(),
        val rules: List<Map<String, Any?>> = emptyList(),
    )

    /**
     * The rule sets the preset routes by, as **files**.
     *
     * Not `type: "remote"`: sing-box fetches a remote rule set inside `start()`
     * and the router runs those fetches with `FastFail`, so a first fetch that
     * fails — offline, blocked, CDN down — fails the whole start and the tunnel
     * never comes up. That is the exact deadlock this preset has to avoid, so
     * the app downloads the files and hands over paths; when they are missing
     * the preset is simply not emitted and everything falls through to `final`.
     *
     * `format` is inferred from the `.srs` extension anyway; stating it means a
     * renamed file cannot quietly change how it is parsed.
     */
    private fun mainlandRuleSets(mainland: MainlandRuleSets): List<Map<String, Any?>> = listOf(
        linkedMapOf<String, Any?>(
            "type" to "local",
            "tag" to GEOSITE_CN_TAG,
            "format" to "binary",
            "path" to mainland.geositeCnPath,
        ),
        linkedMapOf<String, Any?>(
            "type" to "local",
            "tag" to GEOIP_CN_TAG,
            "format" to "binary",
            "path" to mainland.geoipCnPath,
        ),
    )

    /**
     * The ad set, on the same terms as the preset's: a local file the app
     * downloaded, never a remote one. An ads list that cannot be fetched must
     * not be able to keep the tunnel down.
     */
    private fun adsRuleSet(path: String): Map<String, Any?> = linkedMapOf(
        "type" to "local",
        "tag" to ADS_TAG,
        "format" to "binary",
        "path" to path,
    )

    /**
     * DNS, with or without the preset.
     *
     * The problem the preset solves is not speed but honesty: a poisoned answer
     * for a domestic name resolves to an address that is not in geoip-cn, so the
     * address-based rule sends domestic traffic abroad. Resolving domestic names
     * through a domestic resolver keeps the two rules agreeing with each other.
     *
     * `optimistic` is sing-box's own serve-stale cache: a cached answer is
     * returned immediately and refreshed behind the request, which is what makes
     * the second lookup of a name fast rather than a second round trip. It goes
     * on the `dns` block, where sing-box actually defines it; see the note on
     * the preset branch below.
     */
    private fun dnsBlock(mainland: MainlandRuleSets?, proxyTag: String): Map<String, Any?> {
        if (mainland == null) {
            // New DNS server format. The legacy {"address": "local"} shape was
            // deprecated in sing-box 1.12 and REMOVED in 1.14, where it fails
            // config decode with an explicit migration message.
            return linkedMapOf(
                "strategy" to DNS_STRATEGY,
                "servers" to listOf(
                    linkedMapOf<String, Any?>(
                        "type" to "local",
                        "tag" to LOCAL_DNS_TAG,
                    ),
                ),
            )
        }
        return linkedMapOf(
            "strategy" to DNS_STRATEGY,
            "servers" to listOf(
                linkedMapOf<String, Any?>(
                    "type" to "local",
                    "tag" to LOCAL_DNS_TAG,
                ),
                linkedMapOf<String, Any?>(
                    "type" to "https",
                    "tag" to DNS_CN_TAG,
                    "server" to DNS_CN_SERVER,
                    // Deliberately no `detour`. It would have to name an
                    // outbound, and the only one that means "not through the
                    // tunnel" is the built-in `direct`, which carries no
                    // options — sing-box refuses that pairing outright with
                    // "detour to an empty direct outbound makes no sense" and
                    // the tunnel never starts. Left unset, the dialer falls
                    // back to the default outbound, which is that same direct
                    // one, so the resolver still leaves the device directly.
                    "domain_resolver" to LOCAL_DNS_TAG,
                ),
                linkedMapOf<String, Any?>(
                    "type" to "https",
                    "tag" to DNS_PROXY_TAG,
                    "server" to DNS_PROXY_SERVER,
                    // Verified with the shipped 1.14.3 binary: an https DNS
                    // server accepts a tls block, and server_name is the field
                    // that decides which name the certificate must cover.
                    "tls" to linkedMapOf<String, Any?>(
                        "enabled" to true,
                        "server_name" to DNS_PROXY_TLS_NAME,
                    ),
                    // The tag this build actually defines: `auto` for a group,
                    // `proxy` for a single node. Naming one the config does not
                    // define fails the start with "outbound detour not found",
                    // which is what a hardcoded `auto` did for every
                    // single-node connection.
                    "detour" to proxyTag,
                    "domain_resolver" to LOCAL_DNS_TAG,
                ),
            ),
            "rules" to listOf(
                // A name only the LAN can answer must not be sent anywhere.
                // This matches the *name*, before anything is resolved, which
                // is the only way to know a question is about the LAN without
                // first asking a resolver that is not on it.
                linkedMapOf<String, Any?>(
                    "domain_suffix" to LAN_DNS_SUFFIXES,
                    "action" to "route",
                    "server" to LOCAL_DNS_TAG,
                ),
                // Domestic names next. This is the rule that keeps a poisoned
                // answer from sending domestic traffic abroad, and putting it
                // first means the common case costs exactly one lookup.
                linkedMapOf<String, Any?>(
                    "rule_set" to GEOSITE_CN_TAG,
                    "action" to "route",
                    "server" to DNS_CN_TAG,
                ),
                // Everything else resolves through the tunnel, and then the
                // answer is inspected: a private one means a split-horizon
                // name, and only the LAN resolver has the right address.
                //
                // `evaluate` fetches the response once and `match_response`
                // decides on it — the form 1.14 defines. The bare
                // `ip_is_private` this replaces was a Legacy Address Filter:
                // it resolved through `local` first, rejected every public
                // answer, and then resolved again through the rule that
                // actually applied. Every new name paid two lookups for an
                // answer that was thrown away, and 1.16 removes the form
                // outright.
                //
                // The evaluated server is the route's `final`, so the final
                // lookup is a cache hit rather than a second round trip.
                linkedMapOf<String, Any?>(
                    "action" to "evaluate",
                    "server" to DNS_PROXY_TAG,
                ),
                linkedMapOf<String, Any?>(
                    "match_response" to true,
                    "ip_is_private" to true,
                    "action" to "route",
                    "server" to LOCAL_DNS_TAG,
                ),
            ),
            "final" to DNS_PROXY_TAG,
            // `optimistic` is sing-box's serve-stale cache: a cached answer is
            // returned at once and refreshed behind the request. It is a field
            // of the `dns` block, NOT of a server — set on a server it is not a
            // field sing-box knows and the whole config is rejected with
            // `dns.servers[0].optimistic: json: unknown field "optimistic"`.
            //
            // Set only with the preset, because the preset-off path is meant to
            // stay byte-for-byte what it was before this feature existed, and a
            // cache is a behaviour change. The boolean form is enough: it
            // defaults the staleness window to three days.
            "optimistic" to true,
        )
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
        // IPv4 only. An IPv6 address here makes the OS route every AAAA
        // destination into the tunnel; on a Wi-Fi network with no IPv6 egress
        // the direct outbound then fails with `network is unreachable`, but
        // the app has already completed its TCP handshake with the tun and
        // sees a reset instead of an unreachable network — so Happy Eyeballs
        // never falls back to IPv4. Chrome shows ERR_CONNECTION_RESET on
        // domestic sites while curl to the same IPv4 address works. Leaving
        // IPv6 off the interface makes the OS reject AAAA immediately, which
        // is the behaviour the IPv4-only DNS strategy already assumes.
        "address" to listOf("172.19.0.1/30"),
        "mtu" to 9000,
        // hijack: sing-box answers DNS itself instead of letting queries reach
        // the tun and be dropped. Without it an Android app resolves nothing
        // and reports "cannot use the network".
        "dns_mode" to "hijack",
        "auto_route" to true,
        "strict_route" to false,
        // Mixed: TCP on the kernel stack, UDP through gVisor. It only works
        // when the process is NOT excluded from the VPN by UID — the kernel
        // stack completes TCP through a listener owned by this app, and a UID
        // exclusion sends that listener's SYN-ACKs outside the tunnel (client
        // SYN_SENT, listener SYN_RECV, zero bytes). Starveil therefore exempts
        // individual outbound sockets with VpnService.protect() instead; see
        // StarveilPlatformInterface.autoDetectInterfaceControl.
        //
        // Measured on device (same node, bypass preset, 10 s samples): mixed
        // 10-16 MB/s domestic (mean ~13) vs gVisor ~10 MB/s; foreign is
        // unchanged at ~4.0 MB/s.
        "stack" to "mixed",
    )

    private fun render(
        outbounds: List<Map<String, Any?>>,
        endpoints: List<Map<String, Any?>>,
        finalTag: String,
        rules: List<Rule>,
        mainland: MainlandRuleSets? = null,
        perAppRules: List<Map<String, Any?>> = emptyList(),
        preset: RoutePreset = RoutePreset.GLOBAL_PROXY,
        adsPath: String? = null,
        rulePacks: List<InstalledRulePack> = emptyList(),
        proxyTag: String = finalTag,
        localApi: LocalApiConfig? = null,
        cacheFileName: String = "cache.db",
        localSubnets: List<String> = emptyList(),
        subscriptionProxyPort: Int? = null,
    ): String {
        val config = linkedMapOf<String, Any?>(
            "log" to linkedMapOf("level" to "warn", "timestamp" to true),
            "dns" to dnsBlock(mainland, finalTag),
            "inbounds" to buildList {
                add(tunInbound())
                // Loopback only, and only when the user asked for subscription
                // fetches to leave through the tunnel: Android keeps this app's
                // own sockets out of its own tun, so the only way to reach the
                // node from inside the app is a proxy the kernel is listening
                // on. A port nobody asked for is a port anything on the device
                // could use.
                subscriptionProxyPort?.let { port ->
                    add(
                        linkedMapOf<String, Any?>(
                            "type" to "socks",
                            "tag" to SUBSCRIPTION_IN_TAG,
                            "listen" to "127.0.0.1",
                            "listen_port" to port,
                        ),
                    )
                }
            },
            "outbounds" to outbounds,
        )
        // WireGuard's endpoint goes here, not in `outbounds`: sing-box 1.13
        // removed the outbound form and fails config construction on it.
        // Omitted when empty, so a config with no endpoint is unchanged.
        if (endpoints.isNotEmpty()) {
            config["endpoints"] = endpoints
        }
        config["route"] = linkedMapOf<String, Any?>(
            "rules" to routeRules(
                rules,
                finalTag,
                proxyTag,
                mainland,
                perAppRules,
                preset,
                adsPath,
                rulePacks,
                localSubnets,
            ),
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
        val ruleSets = buildList {
            if (mainland != null) addAll(mainlandRuleSets(mainland))
            if (adsPath != null) add(adsRuleSet(adsPath))
            rulePacks.forEach { installed ->
                add(
                    linkedMapOf<String, Any?>(
                        "type" to "local",
                        "tag" to installed.pack.tag,
                        "format" to "binary",
                        "path" to installed.path,
                    ),
                )
            }
        }
        if (ruleSets.isNotEmpty()) {
            // Inserted rather than appended so the emitted JSON reads in the
            // order it is evaluated: the sets are declared, then the rules
            // that reference them.
            val route = config["route"] as LinkedHashMap<String, Any?>
            route["rule_set"] = ruleSets
        }
        // How the *proxy server's own* name is resolved, as opposed to what
        // apps are told. Set unconditionally: with one DNS server the kernel
        // picks it silently, with several it refuses to choose at all
        // ("missing `route.default_domain_resolver`"), and the strategy is the
        // part that matters here.
        //
        // `dns.strategy` is ipv4_only so apps never receive an AAAA they
        // cannot route into a v4-only tun. That reasoning does not extend to
        // the node: its address is dialled by the kernel on the underlying
        // network, through a protected socket, where IPv6 works — and a node
        // whose domain has only an AAAA record would otherwise never resolve,
        // which looks exactly like "connected, no internet". `prefer_ipv4`
        // keeps A first and falls back to AAAA only when there is no A.
        //
        // `local` is the only correct server pick: it is the platform
        // resolver, and the tunnel that `dns-proxy` dials through is not up
        // yet at that point.
        (config["route"] as LinkedHashMap<String, Any?>)["default_domain_resolver"] =
            linkedMapOf<String, Any?>(
                "server" to LOCAL_DNS_TAG,
                "strategy" to NODE_RESOLVE_STRATEGY,
            )
        // Present, though empty. sing-box only builds its traffic manager —
        // and therefore only reports uplink/downlink to the CommandClient —
        // when `experimental.clash_api` is set (box.go: `needClashAPI`).
        // Without it every StatusMessage carries zero, so the dashboard's
        // rates and totals sit at 0 B/s forever even while traffic flows.
        // An empty controller opens no HTTP listener: the server is created
        // but never bound.
        config["experimental"] = linkedMapOf<String, Any?>(
            // Off is an empty object, not an omitted key: the traffic manager
            // only exists when `clash_api` is present, and the dashboard's
            // rates depend on it. On adds a listener on loopback and the token
            // the kernel checks every request against.
            "clash_api" to if (localApi == null) {
                emptyMap<String, Any?>()
            } else {
                linkedMapOf<String, Any?>(
                    "external_controller" to localApi.controller,
                    "secret" to localApi.token,
                )
            },
            // Named even when nothing is cached: the daemon forces the cache
            // file on (it passes itself as the platform log writer), so the
            // path is always opened and has to be a distinct one per policy.
            "cache_file" to linkedMapOf<String, Any?>("path" to cacheFileName),
            // M4 memory tuning. `gc_percent` is GOGC by another name: the
            // kernel calls debug.SetGCPercent with it at box start
            // (option/debug.go -> box.go:applyDebugOptions), so it applies
            // after the Go runtime is already up and needs no env-var timing.
            //
            // `memory_limit` is Go's soft memory limit. The kernel divides it
            // by 1.5 before calling debug.SetMemoryLimit, leaving room for the
            // JVM and native allocations in the same process — so the value
            // here is the process budget, not the Go heap. 96 MiB becomes a
            // 64 MiB Go limit. Measured on the vivo (docs/11): the Go heap is
            // only ~9 MB, so this limit never binds and the PSS target of
            // 80 MB is not reached — the floor is shared code and Compose,
            // not GOGC. Kept as a ceiling for larger node lists.
            //
            // FreeOSMemory at a PSS watermark is deliberately not wired: the
            // only exposed path is the unauthenticated debug HTTP server
            // (`experimental.debug.listen`), which also serves pprof heap
            // profiles — those can contain node addresses and UUIDs. The
            // kernel already calls FreeOSMemory after every start/reload
            // (daemon/started_service.go), which is the safe part of that idea.
            "debug" to linkedMapOf<String, Any?>(
                "gc_percent" to GC_PERCENT,
                "memory_limit" to MEMORY_BUDGET_BYTES,
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

        /** The loopback inbound a subscription fetch dials, when it is on. */
        const val SUBSCRIPTION_IN_TAG = "subscription-in"

        /** Where that inbound listens. Loopback only. */
        const val SUBSCRIPTION_PROXY_PORT = 2080
        const val LOCAL_DNS_TAG = "local"
        const val DNS_CN_TAG = "dns-cn"
        const val DNS_PROXY_TAG = "dns-proxy"
        const val GEOSITE_CN_TAG = "geosite-cn"
        const val ADS_TAG = "ads"

        /**
         * Destinations that are never proxied, whatever network this is.
         *
         * `224.0.0.0/4` is here for discovery: SSDP and mDNS advertise over
         * multicast, and a proxied multicast packet is a device nobody finds.
         * The private ranges are deliberately absent — they are added from the
         * phone's own interface, so that only the network it is standing in is
         * kept direct.
         */
        val ALWAYS_DIRECT_CIDRS = listOf(
            "127.0.0.0/8",
            "::1/128",
            "fe80::/10",
            "224.0.0.0/4",
        )
        const val GEOIP_CN_TAG = "geoip-cn"

        /** GOGC: collect when the heap has grown 50%, not 100%. */
        const val GC_PERCENT = 50

        /**
         * The process budget passed to `debug.memory_limit`.
         *
         * The kernel turns this into a Go heap soft limit of two thirds of the
         * value, so 96 MiB leaves 64 MiB for Go and the rest for the JVM and
         * native code. The vivo measurement (docs/11) found the live Go heap
         * around 9 MB, so this is a ceiling, not a working limit; lower it
         * only with a PSS measurement in hand, because below the live heap the
         * GC runs continuously instead of collecting garbage.
         */
        const val MEMORY_BUDGET_BYTES = 96 * 1024 * 1024

        /** Tag prefix for an outbound that exists only because an app names it. */
        const val PER_APP_TAG_PREFIX = "per-app-"

        /** AliDNS over HTTPS: reachable from the mainland without a proxy. */
        const val DNS_CN_SERVER = "223.5.5.5"

        /** Cloudflare, reached through the tunnel, for everything else. */
        const val DNS_PROXY_SERVER = "1.1.1.1"

        /**
         * The name the proxy resolver's certificate is issued to.
         *
         * Dialling 1.1.1.1 and verifying the certificate against 1.1.1.1 only
         * works when the endpoint returns a certificate that carries the
         * address as an IP SAN. Through a Cloudflare-fronted relay it does not:
         * the answer is a certificate for a name, and the handshake dies with
         * "x509: cannot validate certificate for 1.1.1.1 because it doesn't
         * contain any IP SANs". Every foreign name then fails to resolve, and
         * the tunnel looks connected while nothing loads.
         *
         * So the address stays as the dial target — it needs no lookup of its
         * own — and the name goes in as the TLS server name, which is what the
         * certificate is checked against and what Cloudflare expects in the
         * SNI anyway.
         */
        const val DNS_PROXY_TLS_NAME = "cloudflare-dns.com"

        /**
         * IPv4 only, like every mainstream Android client's default.
         *
         * With `as_is`, a dual-stack name returns AAAA too, and a direct dial
         * to that address fails on a Wi-Fi network with no IPv6 route:
         * `dial tcp [2408:...]:443: connect: network is unreachable`. Chrome
         * surfaces that as ERR_CONNECTION_RESET on domestic sites while the
         * IPv4 path is fine, so the resolver must not hand out AAAA.
         */
        const val DNS_STRATEGY = "ipv4_only"

        /**
         * What the node's own hostname is resolved with.
         *
         * Separate from [DNS_STRATEGY] on purpose: that one exists so apps do
         * not get AAAA answers, while this one exists so a node that has only
         * an AAAA record can still be reached. A first, AAAA only when there
         * is no A.
         */
        const val NODE_RESOLVE_STRATEGY = "prefer_ipv4"

        /**
         * Names that can only be answered on the LAN.
         *
         * Matched as suffixes, so `.lan` covers `nas.lan`. These are the
         * special-use names that never resolve publicly; a split-horizon name
         * on a public domain is caught by the response check instead.
         */
        val LAN_DNS_SUFFIXES = listOf(".lan", ".local", ".home.arpa", ".internal")
    }
}


/** A config could not be built from the given profile. Carries no credentials. */
class ConfigBuildException(message: String) : IllegalArgumentException(message)
