package com.justnpcx.starveil.core.model

/**
 * How the tunnel decides where traffic goes, before any user rule is consulted.
 *
 * Three policies, not a pile of switches: "direct everything" and "proxy
 * everything" are not the absence of a preset, they are presets — the old
 * boolean could only say "bypass mainland" or "say nothing", and "say nothing"
 * happened to mean "proxy everything", which the empty-state text then
 * contradicted.
 */
enum class RoutePreset {
    /** Domestic names and addresses direct, everything else through the tunnel. */
    BYPASS_MAINLAND,

    /** Everything through the tunnel. The default. */
    GLOBAL_PROXY,

    /** Everything direct. The tunnel stays up but forwards nothing. */
    GLOBAL_DIRECT;

    companion object {
        fun fromStorage(value: String?): RoutePreset =
            entries.firstOrNull { it.name == value } ?: GLOBAL_PROXY
    }
}
