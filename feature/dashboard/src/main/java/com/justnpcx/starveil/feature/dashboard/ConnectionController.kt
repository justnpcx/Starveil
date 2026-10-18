package com.justnpcx.starveil.feature.dashboard

import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.data.ruleset.MainlandRuleSetStore
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KillSwitchController
import com.justnpcx.starveil.core.kernel.TrafficPolicy
import com.justnpcx.starveil.core.kernel.config.ConfigBuildException
import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
import com.justnpcx.starveil.core.model.AppRoute
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.PerAppOutbound
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.displayName
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.kernel.timing.ConnectClock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * What a connect or disconnect attempt did.
 *
 * An outcome rather than a UI state: the dashboard maps it to labels and
 * notices, the quick-settings tile maps it to a tile state, and neither has to
 * know how the other says things.
 */
sealed interface ConnectionOutcome {
    /** The tunnel is up. [label] is what to name it — a node or a group. */
    data class Connected(val label: String?) : ConnectionOutcome

    /** The tunnel is down and traffic is allowed. */
    data object Disconnected : ConnectionOutcome

    /**
     * The Kill Switch is holding traffic back: either the connect was refused
     * outright (always-block) or the disconnect deliberately left the tunnel
     * standing with nothing forwarding it.
     */
    data class Blocked(val started: Boolean = true, val reason: String? = null) : ConnectionOutcome

    /** Nothing is selected to connect to. */
    data object NoTarget : ConnectionOutcome

    /** The selected target cannot be turned into a config. */
    data object ConfigUnbuildable : ConnectionOutcome

    /** The kernel refused to start. [reason] is already redacted. */
    data class Refused(val reason: String?) : ConnectionOutcome
}

/**
 * Building and starting the tunnel, in one place.
 *
 * It lives here because the dashboard is where this logic grew, and both the
 * dashboard and the quick-settings tile need exactly the same answer: which
 * target, which rules, whether the preset's files are on disk, which apps go
 * their own way, and what the Kill Switch says about starting at all.
 * Duplicating that for the tile would be two implementations of one policy,
 * and the second one would drift.
 */
@Singleton
class ConnectionController @Inject constructor(
    private val kernel: KernelBridge,
    private val profiles: ProfileRepository,
    private val groups: GroupRepository,
    private val rules: RuleRepository,
    private val preferences: PreferencesRepository,
    private val configBuilder: ConfigBuilder,
    private val mainlandRuleSets: MainlandRuleSetStore,
    private val timing: ConnectClock,
) {

    /**
     * Where a config is built.
     *
     * A property rather than a constructor parameter: Hilt would need a
     * qualified binding for a dispatcher, and the only caller that ever wants
     * a different one is a test that asserts immediately after `connect()` and
     * cannot see past the thread hop. Production never touches this.
     */
    internal var configDispatcher: CoroutineDispatcher = Dispatchers.Default

    suspend fun connect(): ConnectionOutcome {
        // Starts the stopwatch the first proxied connection will stop.
        timing.markConnectStart()
        // Kill Switch, panic mode. Asked as "would traffic be allowed if we
        // were up?", so the decision comes from the one policy function rather
        // than a second reading of the mode here.
        val alwaysBlocked = KillSwitchController.policyFor(
            preferences.killSwitchMode.first(),
            connected = true,
        ) == TrafficPolicy.BLOCK
        if (alwaysBlocked) {
            // block(), not start(buildBlocking()): the bridge owns what
            // "blocking" means, and reports it through `blocking`.
            val result = kernel.block()
            return ConnectionOutcome.Blocked(
                started = result.isSuccess,
                reason = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() },
            )
        }

        val target = resolveTarget()
        val rules = rules.observeAllOrdered().first()
        // Resolved before the build, and only when the switch is on: a failed
        // download leaves this null and the config comes out without the
        // preset, which is a working state rather than a failed connect.
        val preset = preferences.routePreset.first()
        val mainland = if (preset == RoutePreset.BYPASS_MAINLAND) {
            mainlandRuleSets.ensure()
        } else {
            null
        }
        // Stacked on top of any preset, and independent of it: a failed
        // download leaves ads unblocked rather than taking the routing with it.
        val ads = if (preferences.blockAds.first()) mainlandRuleSets.ensureAds() else null

        val config = try {
            val perApp = resolvePerApp(preferences.perAppRoutes.first())
            // Off the caller's dispatcher: a subscription of a thousand nodes
            // is 5 ms here and an order of magnitude more on a low-end phone,
            // and this is called from the main thread's scope. The builder
            // itself stays synchronous and pure — the dispatcher belongs to
            // whoever is about to block.
            withContext(configDispatcher) {
                when (target) {
                    is Target.Node -> configBuilder.build(
                        target.profile,
                        rules,
                        mainland = mainland,
                        perApp = perApp,
                        preset = preset,
                        adsPath = ads,
                    )

                    is Target.Group -> configBuilder.build(
                        target.members,
                        rules,
                        target.mode,
                        mainland,
                        perApp = perApp,
                        preset = preset,
                        adsPath = ads,
                    )

                    Target.None -> null
                }
            } ?: return ConnectionOutcome.NoTarget
        } catch (e: ConfigBuildException) {
            return ConnectionOutcome.ConfigUnbuildable
        }

        val result = kernel.start(config)
        return if (result.isSuccess) {
            ConnectionOutcome.Connected(target.label())
        } else {
            // The bridge reports a redacted reason (PRD §8.3: tell the truth on
            // failure, never a bare "failed to start").
            ConnectionOutcome.Refused(result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() })
        }
    }

    /**
     * Rebuild the running tunnel from the current preferences.
     *
     * For callers that change a policy without a UI — a scene, mostly. Does
     * nothing when no tunnel is up: there is nothing to rebuild, and the next
     * connect reads the new preference anyway.
     */
    suspend fun reapply(): ConnectionOutcome {
        if (kernel.state.value != com.justnpcx.starveil.core.kernel.KernelState.Running) {
            return ConnectionOutcome.Disconnected
        }
        return connect()
    }

    /**
     * "Block on disconnect" swaps the running config for one that forwards
     * nothing. Tearing the tunnel down here is exactly the leak that mode
     * exists to prevent: whatever was in flight would go out directly.
     *
     * A reload rather than a stop-and-start: the tun never goes away, so the
     * block begins at the config swap and not a moment later.
     */
    suspend fun disconnect(): ConnectionOutcome {
        val blocking = KillSwitchController.policyFor(
            preferences.killSwitchMode.first(),
            connected = false,
        ) == TrafficPolicy.BLOCK
        if (!blocking) {
            kernel.stop()
            return ConnectionOutcome.Disconnected
        }
        val result = kernel.block()
        return ConnectionOutcome.Blocked(
            started = result.isSuccess,
            reason = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * What the tunnel should point at.
     *
     * A group carries its name and members rather than the [ProxyGroup] itself,
     * so the connect path cannot accidentally re-read a group that changed
     * underneath it.
     */
    private sealed interface Target {
        data object None : Target

        data class Node(val profile: Profile) : Target

        data class Group(val name: String, val members: List<Profile>, val mode: GroupMode) : Target

        /** Null for [None]: there is no name to show, and no copy belongs here. */
        fun label(): String? = when (this) {
            is Node -> profile.displayName()
            is Group -> name
            None -> null
        }
    }

    /**
     * Turns the stored selection into something buildable.
     *
     * A group only counts as a target when its mode produces a group outbound
     * and it has at least one member left: a plain folder is not something you
     * connect *through*, and an empty group has nothing to build from. Both
     * degrade to [Target.None] rather than failing later.
     */
    private suspend fun resolveTarget(): Target {
        return when (val selected = preferences.connectionTarget.first()) {
            is ConnectionTarget.Group -> {
                val group: ProxyGroup = groups.getById(selected.id) ?: return Target.None
                if (group.mode == GroupMode.MANUAL) return Target.None
                val members = group.profileIds.mapNotNull { profiles.getById(it) }
                if (members.isEmpty()) return Target.None
                Target.Group(group.name, members, group.mode)
            }

            is ConnectionTarget.Node ->
                profiles.getById(selected.id)?.let { Target.Node(it) } ?: Target.None

            ConnectionTarget.None -> Target.None
        }
    }

    /**
     * Per-app targets, resolved to the nodes they name.
     *
     * Grouped by target so two apps pointing at the same node share one
     * outbound and one connection pool. Sorted by profile id so the same
     * settings always produce the same config.
     *
     * A node that no longer exists is dropped rather than failing the connect:
     * that app then follows the tunnel's default, which is what would have
     * happened before it was ever configured. Refusing to connect because
     * someone deleted a node would be a worse trade.
     */
    private suspend fun resolvePerApp(routes: Map<String, AppRoute>): List<PerAppOutbound> {
        if (routes.isEmpty()) return emptyList()
        val packagesByTarget = linkedMapOf<AppRoute, MutableSet<String>>()
        routes.forEach { (packageName, route) ->
            packagesByTarget.getOrPut(route) { mutableSetOf() } += packageName
        }
        val result = mutableListOf<PerAppOutbound>()
        packagesByTarget.remove(AppRoute.Direct)?.let { packages ->
            result += PerAppOutbound.Direct(packages)
        }
        result += packagesByTarget.mapNotNull { (route, packages) ->
            val profileId = (route as? AppRoute.Node)?.profileId ?: return@mapNotNull null
            profiles.getById(profileId)?.let { PerAppOutbound.Node(packages, it) }
        }.sortedBy { it.profile.id }
        return result
    }
}
