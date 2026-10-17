package com.justnpcx.starveil.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a group of profiles.
 *
 * `mode` stores [com.justnpcx.starveil.core.model.GroupMode].name.
 * `subscriptionUrl` is sensitive (may contain tokens) and must never be
 * logged — see the redaction rule in the kernel bridge.
 *
 * `region` stores a [com.justnpcx.starveil.core.model.NodeRegion].name and
 * marks a group whose membership is DERIVED from node names rather than stored
 * on the profiles. That is what lets one node be in its subscription group and
 * in "香港" at the same time: a profile's `groupId` stays with the subscription,
 * and the region group computes its members on read. Storing membership instead
 * would be undone by the next subscription refresh, which deletes and re-inserts
 * every profile in its group.
 *
 * NOTE: the domain [com.justnpcx.starveil.core.model.ProxyGroup] carries
 * `profileIds`, but that list is DERIVED (profiles carry `groupId`, or the name
 * classifies to a region), not stored here. Repository composes it on read.
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val mode: String,
    val subscriptionUrl: String? = null,
    val region: String? = null,
)
