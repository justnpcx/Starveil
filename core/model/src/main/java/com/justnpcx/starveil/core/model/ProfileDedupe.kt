package com.justnpcx.starveil.core.model

/**
 * One node per (protocol, address, port).
 *
 * A subscription that lists the same server twice — a provider's "premium"
 * and "standard" rows pointing at one host, or a refresh that appended
 * instead of replacing — would otherwise grow the list on every update.
 *
 * The survivor is the one with the longer name: providers that repeat a node
 * usually do it to give the better one a fuller label ("Tokyo · premium ·
 * IPLC"), and keeping the shorter name loses the only thing that told them
 * apart. Ties keep the first, so the order a subscription came in is stable.
 *
 * Pure, and stable: the output preserves the order of the survivors.
 */
fun dedupeProfiles(profiles: List<Profile>): List<Profile> {
    val best = LinkedHashMap<String, Profile>()
    for (profile in profiles) {
        val key = "${profile.type}|${profile.server.lowercase()}|${profile.port}"
        val existing = best[key]
        if (existing == null || profile.name.length > existing.name.length) {
            best[key] = profile
        }
    }
    return best.values.toList()
}
