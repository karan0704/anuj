package com.karan.anuj.core.data.db

import com.karan.anuj.core.domain.record.RecordStamps

/**
 * The three record-keeping columns every table carries.
 *
 * Each entity includes this with `@Embedded val stamps: StampColumns` rather
 * than declaring the columns itself, so the names and types are identical in
 * every table and are defined in exactly one place.
 */
data class StampColumns(
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
) {
    fun toDomain(): RecordStamps = RecordStamps(createdAt, updatedAt, deletedAt)

    companion object {
        fun from(stamps: RecordStamps): StampColumns =
            StampColumns(stamps.createdAt, stamps.updatedAt, stamps.deletedAt)
    }
}
