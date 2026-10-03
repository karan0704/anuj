package com.karan.anuj.core.domain.record

/**
 * The record-keeping values every stored row carries: when it was created,
 * when it last changed, and when it was deleted.
 *
 * Deleting only fills [deletedAt]; the row stays in the database so undo, the
 * trash screen and change history keep working.
 */
data class RecordStamps(
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null

    /** The row was edited at [now]. */
    fun touched(now: Long): RecordStamps = copy(updatedAt = now)

    /** The row was moved to the trash at [now]. */
    fun deleted(now: Long): RecordStamps = copy(updatedAt = now, deletedAt = now)

    /** The row was brought back from the trash at [now]. */
    fun restored(now: Long): RecordStamps = copy(updatedAt = now, deletedAt = null)

    companion object {
        /** A row that comes into existence at [now]. */
        fun created(now: Long): RecordStamps = RecordStamps(createdAt = now, updatedAt = now)
    }
}
