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
 * NOTE: the domain [com.justnpcx.starveil.core.model.ProxyGroup] carries
 * `profileIds`, but that list is DERIVED (profiles carry `groupId`), not
 * stored here. Repository composes it on read.
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val mode: String,
    val subscriptionUrl: String? = null,
)
