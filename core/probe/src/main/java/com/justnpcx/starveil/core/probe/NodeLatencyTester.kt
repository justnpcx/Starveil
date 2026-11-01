package com.justnpcx.starveil.core.probe

import android.content.Context
import com.justnpcx.starveil.core.probe.R
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.LATENCY_UNREACHABLE
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.WEIGHT_MANUAL
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Measures the latency of the currently selected node and stores it.
 *
 * The probe itself is [NodeProbe] — the same one the health sampler uses, so
 * the number the button reports and the number the curve draws come from the
 * same measurement rather than from two implementations that could drift.
 *
 * What is measured is the network round-trip to the node, not the proxy's own
 * handshake: a node that accepts TCP but rejects the protocol still reads as
 * fast. Proving the protocol works is what connecting is for.
 *
 * Lives in :core:probe with the rest of the probing; the Activity injects it
 * and the Nodes screen only receives a callback.
 *
 * Always returns a user-facing message, so a failed measurement is never
 * silent — "nothing happened" was the previous behaviour and read as a broken
 * button. It also says whether a number came back, because the page's refresh
 * action reports "refreshed" only when there is something to show for it.
 *
 * This writes `Profile.latencyMs` (the last measured value the card shows) and
 * also appends to the health history at [WEIGHT_MANUAL] — half a background
 * sample. It counts because it is a real measurement and the user asked for it;
 * it counts half because it is a single instant taken while someone watched,
 * not a record of how the node behaves over time. Letting a burst of taps
 * outvote the sweep is exactly the failure the weight prevents.
 */
/** What a measurement produced: the sentence to show, and whether it is a number. */
data class LatencyTestResult(
    val message: String,
    /** True only when the probe reached the node and a latency was stored. */
    val measured: Boolean,
)

@Singleton
class NodeLatencyTester @Inject constructor(
    @ApplicationContext private val context: Context,
    private val probe: NodeProbe,
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
    private val health: NodeHealthRepository,
) {

    suspend fun testSelectedNode(): LatencyTestResult {
        val id = preferences.selectedProfileId.first()
        if (id == 0L) return LatencyTestResult(context.getString(R.string.latency_no_node), false)
        return testNode(id)
    }

    /**
     * The same measurement for one named node.
     *
     * Tapping a card's latency badge is the gesture people already make on the
     * card, and testing the *selected* node from there would quietly measure a
     * different node than the one under the finger.
     */
    suspend fun testNode(id: Long): LatencyTestResult {
        val profile = profiles.getById(id)
            ?: return LatencyTestResult(context.getString(R.string.latency_no_node), false)

        // A WireGuard endpoint speaks UDP only, so a TCP handshake to it can
        // only ever fail — reporting that as "unreachable" would be a lie
        // about a node that works.
        if (profile.type == ProfileType.WIREGUARD) {
            return LatencyTestResult(context.getString(R.string.latency_not_applicable), false)
        }

        val nowMs = System.currentTimeMillis()
        return when (val result = probe.probe(profile.server, profile.port)) {
            is ProbeResult.Reached -> {
                profiles.upsert(profile.copy(latencyMs = result.latencyMs))
                health.record(id, result.latencyMs, nowMs, WEIGHT_MANUAL)
                LatencyTestResult(
                    message = context.getString(R.string.latency_measured, result.latencyMs),
                    measured = true,
                )
            }

            ProbeResult.TimedOut -> {
                health.record(id, LATENCY_UNREACHABLE, nowMs, WEIGHT_MANUAL)
                LatencyTestResult(context.getString(R.string.latency_timeout), false)
            }

            ProbeResult.ConnectionFailed -> {
                health.record(id, LATENCY_UNREACHABLE, nowMs, WEIGHT_MANUAL)
                LatencyTestResult(context.getString(manualFailureMessage(ProbeResult.ConnectionFailed)), false)
            }

            ProbeResult.ResolutionFailed -> {
                // No address means no node connection was attempted. Do not
                // turn a private-DNS failure into evidence that the node died.
                LatencyTestResult(context.getString(manualFailureMessage(ProbeResult.ResolutionFailed)), false)
            }
        }
    }
}

internal fun manualFailureMessage(result: ProbeResult): Int = when (result) {
    ProbeResult.ResolutionFailed -> R.string.latency_resolution_failed
    else -> R.string.latency_unreachable
}
