package com.justnpcx.starveil.core.data.entity

import androidx.room.Entity

/**
 * One latency probe, as stored.
 *
 * The primary key is (profileId, atMs) rather than a surrogate id: a probe is
 * identified by *which node* and *when*, and that makes a re-inserted sample
 * an idempotent replace rather than a duplicate. Two probes of the same node
 * in the same millisecond are the same observation.
 *
 * `latencyMs < 0` is a probe that did not come back — a timeout or an
 * unreachable host. It is stored rather than skipped so the loss is visible in
 * the curve (§7.2 draws these as red dots).
 *
 * NO foreign key to `profiles` on purpose. A subscription refresh replaces a
 * group's rows, which mints new ids, and a cascade would wipe the health
 * history on every refresh; a node deleted and restored by the undo (K2) would
 * lose it too. Samples are time-boxed instead: anything older than the window
 * is pruned on the next insert, so a removed node's samples expire on their
 * own within the window.
 *
 * NO extra index on `atMs` either: the table is bounded to the window at one
 * sample per cadence per node, so a scan is cheap, and the per-node queries
 * already ride the primary key.
 */
@Entity(tableName = "latency_samples", primaryKeys = ["profileId", "atMs"])
data class LatencySampleEntity(
    val profileId: Long,
    val atMs: Long,
    val latencyMs: Long,
    /**
     * How much this sample counts, in tenths (10 = full). See
     * [com.justnpcx.starveil.core.model.LatencySample.weightTenths].
     *
     * Stored rather than inferred from who wrote it: the writer is not
     * recoverable at read time, and a column that says what a row means is
     * cheaper to trust than a rule about where rows come from.
     */
    val weightTenths: Int = 10,
)
