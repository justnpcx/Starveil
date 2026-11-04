package com.justnpcx.starveil.core.data.entity

import androidx.room.Entity

/**
 * One counted routing decision: the kernel's rule text plus the outbound tag.
 *
 * The pair is the primary key because the same matcher can carry different
 * actions (a domain routed to the node in one rule and rejected in another),
 * and those are different decisions with different consequences.
 */
@Entity(tableName = "rule_hits", primaryKeys = ["ruleText", "outbound"])
data class RuleHitEntity(
    val ruleText: String,
    val outbound: String,
    val count: Long,
    val lastHitAt: Long,
)

/**
 * The statistics window: when counting started (first hit or last reset).
 *
 * A single row, id 0, rather than a preference: it belongs to the same
 * lifetime as the counts, and a reset has to clear both in one transaction or
 * the page could show counts from before the window it names.
 */
@Entity(tableName = "rule_hit_window")
data class RuleHitWindowEntity(
    @androidx.room.PrimaryKey val id: Int = 0,
    val startedAt: Long,
)
