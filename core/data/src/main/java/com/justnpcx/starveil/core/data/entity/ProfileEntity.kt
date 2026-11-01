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
    /** Pinned to the top of the list and shown by the Favorites filter. */
    val favorite: Boolean = false,
    /**
     * The node this one dials through, as a business key rather than a row id.
     *
     * A subscription refresh is delete-then-insert (ProfileDao.replaceGroup), so
     * every row gets a new id on every refresh — a foreign key here would point
     * at a row that no longer exists after the next update. The key is
     * [com.justnpcx.starveil.core.model.Profile.nodeKey]: the same tuple this app
     * already uses to decide two entries are the same node.
     */
    val frontNodeKey: String? = null,
)
