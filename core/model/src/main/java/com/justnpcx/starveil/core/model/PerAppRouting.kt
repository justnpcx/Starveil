package com.justnpcx.starveil.core.model

/**
 * Where one app's traffic goes.
 *
 * Absence from the map means "follow the tunnel's default target", so there is
 * no Default case: a value meaning "nothing" would be a second way to say what
 * a missing key already says.
 */
sealed interface AppRoute {
    /** Leave the device directly, outside the tunnel. */
    data object Direct : AppRoute

    /** Through this node, whatever the tunnel's default target is. */
    data class Node(val profileId: Long) : AppRoute
}

/**
 * One outbound and the apps that use it, ready for the config builder.
 *
 * Grouped rather than per-app because a config with one outbound per app would
 * dial the same server several times over; the caller groups by target, so two
 * apps pointing at the same node share one outbound and one connection pool.
 */
sealed interface PerAppOutbound {
    /** The apps that take this route. */
    val packages: Set<String>

    data class Direct(override val packages: Set<String>) : PerAppOutbound

    data class Node(
        override val packages: Set<String>,
        val profile: Profile,
    ) : PerAppOutbound
}
