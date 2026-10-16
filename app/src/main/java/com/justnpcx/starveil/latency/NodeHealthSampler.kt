package com.justnpcx.starveil.latency

import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.LATENCY_UNREACHABLE
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
     * Sample the selected node every [INTERVAL_MS] until cancelled.
     *
     * Samples once immediately rather than waiting a full interval: opening the
     * app is exactly when a fresh reading is wanted, and an empty curve for the
     * first 30 seconds would read as "this does nothing".
     */
    suspend fun run() {
        while (true) {
            sampleOnce()
            delay(INTERVAL_MS)
        }
    }

    companion object {
        /**
         * 30 s. The window is 30 minutes (§7.2), so this fills it with ~60
         * samples — enough for the curve to have a shape, and a TCP handshake
         * every 30 s is nothing next to the traffic the tunnel carries.
         */
        const val INTERVAL_MS = 30_000L
    }
}
