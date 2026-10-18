package com.justnpcx.starveil.feature.dashboard

import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.NodeCandidate
import com.justnpcx.starveil.core.model.NodePicker
import com.justnpcx.starveil.core.model.NodeRecommendation
import com.justnpcx.starveil.core.model.NodeRegions
import com.justnpcx.starveil.core.model.nodeHealthOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * What to do when the kernel dies under a running tunnel.
 *
 * Extracted from the view model for one reason: the one-shot retry is a policy
 * with a state machine of its own, and it was the part of that class hardest to
 * see. The view model still owns the UI state and the connect call; this owns
 * the decision — retry, retry elsewhere, or stop and say why.
 */
@Singleton
class DropRecovery @Inject constructor(
    private val profiles: ProfileRepository,
    private val nodeHealth: NodeHealthRepository,
    private val preferences: PreferencesRepository,
) {

    sealed interface Decision {
        /** Not a drop from a live tunnel, or one is already being handled. */
        data object Ignore : Decision

        /** Stop, and put [notice] on screen. */
        data class GiveUp(val notice: DashboardNotice) : Decision

        /**
         * Bring the tunnel back. [switchedTo] is the node the picker chose
         * instead of the one that died, or null when the selection stands.
         */
        data class Reconnect(val switchedTo: NodeRecommendation?) : Decision
    }

    /**
     * One automatic reconnect per user-initiated connection.
     *
     * A reconnect that itself dies must not be retried, or a node that crashes
     * the kernel every time would be retried forever. The user tapping connect
     * again is what grants the next attempt — deliberately not the kernel
     * reporting Running, since a kernel that starts and immediately dies would
     * re-arm itself on every cycle.
     */
    private var canAutoReconnect = true

    /** A user action (or a deliberate rebuild) grants the next automatic retry. */
    fun rearm() {
        canAutoReconnect = true
    }

    /**
     * Decide what a kernel error means.
     *
     * [connected] and [busy] are the view model's own state: a crash while
     * disconnected is not a lost tunnel, and one that arrives while a connect
     * is in flight is that attempt's business, not a second one.
     */
    suspend fun decide(connected: Boolean, busy: Boolean): Decision {
        if (!connected || busy) return Decision.Ignore
        if (!preferences.autoReconnect.first()) return Decision.GiveUp(DashboardNotice.TunnelDropped)
        if (!canAutoReconnect) return Decision.GiveUp(DashboardNotice.TunnelKeepsDropping)

        canAutoReconnect = false

        // Switching before reconnecting, when asked to: retrying the node that
        // just died is the least useful thing to do with a drop. Only for a
        // single-node target — a group already has urltest choosing within it.
        val current = (preferences.connectionTarget.first() as? ConnectionTarget.Node)?.id
        val switch = current != null && preferences.autoSwitchOnDrop.first()
        return Decision.Reconnect(if (switch) nextBest(exclude = current!!) else null)
    }

    /** The picker's answer over the current node list, or null when there is nothing to choose. */
    suspend fun best(): NodeRecommendation? = recommend(exclude = null)

    private suspend fun nextBest(exclude: Long): NodeRecommendation? =
        recommend(exclude)?.takeIf { it.profileId != exclude }

    /**
     * The preferred region is the region of the node the user already chose:
     * that is the only manual preference the app stores, and inventing a second
     * one would be a setting nobody asked for.
     */
    private suspend fun recommend(exclude: Long?): NodeRecommendation? {
        val nodes = profiles.observeAll().first()
        if (nodes.isEmpty()) return null
        val samples = nodeHealth.observeAll().first()
        val selectedId = preferences.selectedProfileId.first()
        // resolve(), not classify(): the same rule the region groups use, so a
        // node the Nodes page files under 美国 is also the one the picker treats
        // as American. Name-only classification would disagree for every node
        // whose name says nothing — which is most imported ones.
        val preferredRegion = nodes.firstOrNull { it.id == selectedId }
            ?.let { NodeRegions.resolve(it.name, it.country) }

        return NodePicker.recommend(
            candidates = nodes
                .filter { it.id != exclude }
                .map { profile ->
                    NodeCandidate(
                        profileId = profile.id,
                        region = NodeRegions.resolve(profile.name, profile.country),
                        health = samples[profile.id]?.let(::nodeHealthOf),
                    )
                },
            preferredRegion = preferredRegion,
            nowMs = System.currentTimeMillis(),
        )
    }
}
