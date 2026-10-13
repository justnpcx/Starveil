package com.justnpcx.starveil.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a routing rule.
 *
 * `action` stores [com.justnpcx.starveil.core.model.RuleAction].name.
 * The order column is named `sort_order`: `order` is a SQL keyword and
 * must never be a raw column name.
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val matcher: String,
    val action: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val enabled: Boolean = true,
)
