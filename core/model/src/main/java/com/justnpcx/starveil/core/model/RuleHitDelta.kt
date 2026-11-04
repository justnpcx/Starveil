package com.justnpcx.starveil.core.model

/**
 * Counts accumulated in memory, waiting to be added to the stored counters.
 *
 * Separate from [RuleHit] because the two mean different things: a delta is
 * "this many since the last flush" and is discarded once written, while a hit
 * is "this many in the whole window" and is what the screens read.
 */
data class RuleHitDelta(
    val ruleText: String,
    val outbound: String,
    val count: Long,
)
