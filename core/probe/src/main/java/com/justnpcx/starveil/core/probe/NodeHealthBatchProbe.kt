package com.justnpcx.starveil.core.probe

import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.LATENCY_UNREACHABLE
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.WEIGHT_FULL
import com.justnpcx.starveil.core.model.cooldownRemainingMs
import com.justnpcx.starveil.core.model.nodeHealthOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * One sweep of every node, for the health history the picker reads.
 *
 * Why this exists: the foreground sampler only ever watches the node that is
 * selected, so a list of fifty nodes produced health for the two or three that
 * had been tried. `NodePicker` scores an unmeasured node at zero, so "pick the
 * best" could only ever choose among those few — the feature was honest but
 * blind.
 *
 * What it measures is a TCP handshake to the user's own servers. No payload,
 * no DNS to a third party, nothing sent to anyone the user did not configure:
 * the same probe the manual button has always made, run for every node.
 *
 * Cost, in one line: one handshake per node per two hours, four at a time,
 * five seconds at most each — a few kilobytes on a phone that is on a network
 * and not low on battery.
 */
@Singleton
class NodeHealthBatchProbe @Inject constructor(
    private val probe: NodeProbe,
    private val profiles: ProfileRepository,
    private val health: NodeHealthRepository,
) {

    /**
     * Probe every node that is due, and return how many were probed.
     *
     * [nowMs] is a parameter so a test can pin the clock, like the rest of the
     * health maths.
     */
    suspend fun run(nowMs: Long = System.currentTimeMillis()): Int {
        val nodes = profiles.observeAll().first()
            // WireGuard is UDP only: a TCP handshake to it can only fail, and
            // recording that would be a lie about a node that works.
            .filter { it.type != ProfileType.WIREGUARD }
        if (nodes.isEmpty()) return 0

        val known = health.observeAll().first()
        val due = nodes.filter { profile ->
            // Same time-out the picker applies: a node parked after three
            // failures is not probed again until it expires, so a dead node
            // cannot cost a handshake every sweep forever.
            cooldownRemainingMs(nodeHealthOf(known[profile.id].orEmpty()), nowMs) == 0L
        }
        if (due.isEmpty()) return 0

        // Chunked rather than one flat fan-out: four sockets at a time is
        // enough to finish a large list quickly and few enough that the radio
        // is not asked to hold dozens of half-open connections at once.
        due.chunked(CONCURRENCY).forEach { chunk ->
            coroutineScope {
                chunk.map { profile -> async { sample(profile, nowMs) } }.awaitAll()
            }
        }
        return due.size
    }

    private suspend fun sample(profile: Profile, nowMs: Long) {
        val latency = when (
            val result = probe.probe(profile.server, profile.port, NodeProbe.BATCH_TIMEOUT_MS)
        ) {
            is ProbeResult.Reached -> result.latencyMs
            ProbeResult.TimedOut, ProbeResult.Failed -> LATENCY_UNREACHABLE
        }
        health.record(profile.id, latency, nowMs, WEIGHT_FULL)
    }

    companion object {
        /** Sockets in flight at once. */
        const val CONCURRENCY = 4
    }
}
