package com.justnpcx.starveil.core.model

/**
 * Recent flow, collapsed to what a glance needs: which rule sent traffic where,
 * and how many times.
 *
 * Fifty connections are rarely fifty different answers. Grouping is what makes
 * them fit on one screen — and the count is the signal: a rule that matched 40
 * of 50 connections is the rule that explains the tunnel's behaviour, and it
 * should not be buried under forty rows of the same thing.
 */
data class FlowGroup(
    /** The kernel's own words for the rule; empty when it did not name one. */
    val rule: String,
    val outcome: FlowOutcome,
    val count: Int,
    /** Newest connection in the group, for stable ordering within a count. */
    val latestMs: Long,
)

/**
 * Groups by (rule, outcome), most frequent first, then most recent.
 *
 * Pure so the ordering can be tested without a kernel: the screen depends on
 * "the biggest group is at the top" and that is exactly the kind of thing that
 * silently reverses when someone edits a comparator.
 */
fun groupFlow(records: List<FlowRecord>): List<FlowGroup> =
    records
        .groupBy { it.rule to it.outcome }
        .map { (key, rows) ->
            FlowGroup(
                rule = key.first,
                outcome = key.second,
                count = rows.size,
                latestMs = rows.maxOf { it.createdAtMs },
            )
        }
        .sortedWith(compareByDescending<FlowGroup> { it.count }.thenByDescending { it.latestMs })
