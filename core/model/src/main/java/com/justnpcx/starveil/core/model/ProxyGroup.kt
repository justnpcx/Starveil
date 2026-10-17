package com.justnpcx.starveil.core.model

/**
 * A group of profiles: a subscription, a manual folder, or a selector /
 * urltest group. The selection mode is data, not UI branching.
 */
data class ProxyGroup(
    val id: Long = 0L,
    val name: String,
    val mode: GroupMode = GroupMode.MANUAL,
    val profileIds: List<Long> = emptyList(),
    /** Subscription URL, if this group comes from one. Never logged. */
    val subscriptionUrl: String? = null,
    /**
     * Set when membership is derived from node names instead of stored.
     *
     * A derived group owns no profiles: its members are whichever nodes
     * classify to this region, recomputed on read. That is what allows a node
     * to belong to its subscription group and to "香港" at once, and it is why
     * a subscription refresh cannot break it.
     */
    val region: NodeRegion? = null,
)

enum class GroupMode {
    /** Plain folder. */
    MANUAL,

    /** User picks one profile; the kernel uses the selection. */
    SELECTOR,

    /** Kernel auto-picks the fastest profile by url test. */
    URL_TEST,
}
