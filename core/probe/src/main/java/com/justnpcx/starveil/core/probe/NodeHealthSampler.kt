package com.justnpcx.starveil.core.probe

import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.LATENCY_UNREACHABLE
import com.justnpcx.starveil.core.model.NodeHealth
import com.justnpcx.starveil.core.model.ProfileType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Keeps the node health curve (§7.2) fed.
 *
 * Probes the *selected* node on a fixed cadence and records each result,
 * including the failures — a timeout is what becomes a loss dot, so it has to
 * be written down rather than skipped.
 *
 * Foreground only, by construction: [run] loops until its caller's coroutine
 * is cancelled, and the caller is the Activity's lifecycle, started only while
 * the app is visible. There is no job held here to forget to cancel, and
 * nothing wakes the radio while the app is closed.
 *
 * A group target is not sampled. `selectedProfileId` is zero when a group is
 * the target, and probing a group's members is a batch job (the audit's
 * `urlTestBatch`), not this.
 */
@Singleton
class NodeHealthSampler @Inject constructor(
    private val probe: NodeProbe,
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
    private val health: NodeHealthRepository,
) {

    /**
     * Probe the selected node once and record the result.
     *
     * Returns the sampled node's id, or null when there was nothing to sample
     * (no target, a group target, a node that has since been deleted, or a
     * WireGuard endpoint).
     *
     * [atMs] is a parameter rather than read here so a test can pin the clock.
     */
    suspend fun sampleOnce(atMs: Long = System.currentTimeMillis()): Long? {
        val id = preferences.selectedProfileId.first()
        if (id == 0L) return null
        val profile = profiles.getById(id) ?: return null

        // A WireGuard endpoint speaks UDP only, so a TCP handshake to it can
        // only ever fail. Recording that as loss would be a lie about a node
        // that works.
        if (profile.type == ProfileType.WIREGUARD) return null

        val latencyMs = when (val result = probe.probe(profile.server, profile.port)) {
            is ProbeResult.Reached -> result.latencyMs
            ProbeResult.TimedOut, ProbeResult.Failed -> LATENCY_UNREACHABLE
        }
        health.record(profileId = id, latencyMs = latencyMs, atMs = atMs)
        return id
    }

    /**
     * Sample the selected node until cancelled.
     *
     * The first [NodeHealth.MIN_SAMPLES] readings are taken at
     * [BURST_INTERVAL_MS] so the curve can be drawn within a minute; after
     * that the cadence is [INTERVAL_MS]. Waiting five steady intervals for the
     * first draw meant two minutes of "collecting" for a curve the user asked
     * for by opening the app.
     *
     * A target that cannot be sampled (a group, a deleted node, WireGuard)
     * falls straight back to the steady interval — bursting on nothing would
     * just wake the radio for no data.
     */
    suspend fun run() {
        var sampledNode: Long? = null
        var burst = 0
        while (true) {
            val node = sampleOnce()
            if (node == null) {
                delay(INTERVAL_MS)
                continue
            }
            if (node != sampledNode) {
                sampledNode = node
                burst = 0
            }
            burst++
            delay(if (burst < NodeHealth.MIN_SAMPLES) BURST_INTERVAL_MS else INTERVAL_MS)
        }
    }

    companion object {
        /**
         * 30 s. The window is 30 minutes (§7.2), so this fills it with ~60
         * samples — enough for the curve to have a shape, and a TCP handshake
         * every 30 s is nothing next to the traffic the tunnel carries.
         */
        const val INTERVAL_MS = 30_000L

        /**
         * 8 s. Five of these fill the minimum sample count in 32 s, so the
         * first curve lands inside the minute; the radio cost is five
         * handshakes, once, when the user has just opened the app.
         */
        const val BURST_INTERVAL_MS = 8_000L
    }
}
