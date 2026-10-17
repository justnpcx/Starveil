package com.justnpcx.starveil.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a proxy node. Deliberately separate from the domain
 * [com.justnpcx.starveil.core.model.Profile]: the entity is a storage shape,
 * the domain model is the app's shape. Repository maps between them.
 *
 * `type` stores [com.justnpcx.starveil.core.model.ProfileType].name.
 * `settingsJson` holds per-protocol settings (REALITY / mux / Hysteria2
 * tuning) as JSON — protocols grow by adding settings objects, never by
 * widening this table into a god-table.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String,
    val server: String,
    val port: Int,
    val latencyMs: Long = -1L,
    val groupId: Long = 0L,
    val settingsJson: String = "{}",
    /** ISO-3166 alpha-2 of the address; null until resolved, or unknown. */
    val country: String? = null,
)
