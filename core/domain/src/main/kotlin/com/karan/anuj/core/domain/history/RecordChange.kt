package com.karan.anuj.core.domain.history

/**
 * One field of one row changing from [oldValue] to [newValue].
 *
 * Values are kept as text so a single history table can describe a change to
 * any column of any table.
 */
data class RecordChange(
    val table: String,
    val rowId: String,
    val field: String,
    val oldValue: String?,
    val newValue: String?,
    val changedAt: Long,
)
