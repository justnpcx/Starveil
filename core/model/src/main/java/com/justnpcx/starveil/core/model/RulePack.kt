package com.justnpcx.starveil.core.model

/** What a subscribed pack does to the domains it matches. */
enum class RulePackAction {
    /** Drop the connection, the same way the built-in ad switch does. */
    REJECT,

    /** Send it through the tunnel, which is the point of the other packs. */
    PROXY,
}

/**
 * One curated rule pack: a `.srs` file from a pinned commit, and what it means.
 *
 * The id is the storage key — it is what a subscription records — so it must
 * never change once a release has shipped.
 */
data class RulePack(
    val id: String,
    val name: String,
    val action: RulePackAction,
    val fileName: String,
    val sha256: String,
    val urls: List<String>,
) {
    /** Cannot collide with `direct`, `block`, `auto`, `proxy` or another pack. */
    val tag: String get() = "pack-$id"
}

/** A pack that is on disk and can be handed to the config builder. */
data class InstalledRulePack(
    val pack: RulePack,
    val path: String,
)
