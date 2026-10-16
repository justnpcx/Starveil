package com.justnpcx.starveil.latency

import android.content.Context
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.ProfileType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
 * Lives in :app so the navigation layer can trigger it; the Nodes screen only
 * receives a callback.
 *
 * Always returns a user-facing message, so a failed measurement is never
 * silent — "nothing happened" was the previous behaviour and read as a broken
 * button.
 *
 * This writes `Profile.latencyMs` (the last measured value the card shows) and
 * deliberately does not append to the health history: the history is the
 * background record the sampler keeps, and a one-off check is not a sample of
 * that. They share the probe, not the store.
 */
@Singleton
class NodeLatencyTester @Inject constructor(
    @ApplicationContext private val context: Context,
    private val probe: NodeProbe,
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
) {

    suspend fun testSelectedNode(): String {
        val id = preferences.selectedProfileId.first()
        if (id == 0L) return context.getString(R.string.latency_no_node)
        val profile = profiles.getById(id) ?: return context.getString(R.string.latency_no_node)

        // A WireGuard endpoint speaks UDP only, so a TCP handshake to it can
        // only ever fail — reporting that as "unreachable" would be a lie
        // about a node that works.
        if (profile.type == ProfileType.WIREGUARD) {
            return context.getString(R.string.latency_not_applicable)
        }

        return when (val result = probe.probe(profile.server, profile.port)) {
            is ProbeResult.Reached -> {
                profiles.upsert(profile.copy(latencyMs = result.latencyMs))
                context.getString(R.string.latency_measured, result.latencyMs)
            }

            ProbeResult.TimedOut -> context.getString(R.string.latency_timeout)
            ProbeResult.Failed -> context.getString(R.string.latency_unreachable)
        }
    }
}

/** Hilt bridge for the Composable that needs it. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NodeLatencyTesterEntryPoint {
    fun nodeLatencyTester(): NodeLatencyTester
}
