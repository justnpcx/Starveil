package com.justnpcx.starveil.core.probe

import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType

/** One measurement policy shared by manual, foreground and batch health probes. */
internal suspend fun probeProfile(
    profile: Profile,
    timeoutMs: Int,
    direct: NodeProbe,
    chain: ChainNodeProbe,
    allProfiles: suspend () -> List<Profile>,
): ProbeResult {
    val key = profile.frontNodeKey
    if (key == null) {
        return if (profile.type == ProfileType.WIREGUARD) {
            ProbeResult.Unsupported
        } else {
            direct.probe(profile.server, profile.port, timeoutMs)
        }
    }
    val front = allProfiles().firstOrNull { it.nodeKey == key }
    if (front == null || front.nodeKey == profile.nodeKey) {
        // The emitter's last-line defence also turns this into a direct config.
        return if (profile.type == ProfileType.WIREGUARD) {
            ProbeResult.Unsupported
        } else {
            direct.probe(profile.server, profile.port, timeoutMs)
        }
    }
    return chain.probe(profile, front, timeoutMs)
}
