package com.justnpcx.starveil.core.model

/**
 * Kill Switch mode (PRD §12 Q3).
 *
 * - [OFF] nothing is blocked.
 * - [BLOCK_ON_DISCONNECT] traffic is allowed only while the kernel is
 *   connected; the moment it drops, new connections are blocked (the
 *   "no leaks after a drop" case).
 * - [ALWAYS_BLOCK] everything is blocked, and the UI shows an explicit
 *   "blocked" state — the panic mode.
 */
enum class KillSwitchMode {
    OFF,
    BLOCK_ON_DISCONNECT,
    ALWAYS_BLOCK,
    ;

    companion object {
        /** Unknown stored values fall back to the safe default. */
        fun fromStorage(raw: String?): KillSwitchMode =
            entries.firstOrNull { it.name == raw } ?: OFF
    }
}

/** Last leak self-check result (minimal M2 version, PRD §3.6 K9). */
data class LeakCheckRecord(
    val checkedAtEpochMs: Long = 0L,
    val blocked: Boolean = false,
)
