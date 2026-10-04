package com.karan.anuj.core.domain.history

import kotlinx.coroutines.flow.Flow

/** Stores and reads back what a row looked like before each edit. */
interface ChangeHistoryRepository {
    suspend fun record(changes: List<RecordChange>)

    /** Changes to one row, newest first. */
    fun observeFor(table: String, rowId: String): Flow<List<RecordChange>>

    /** The latest changes to anything, newest first. */
    fun observeRecent(limit: Int): Flow<List<RecordChange>>

    /** Forgets every change made before [millis]. */
    suspend fun deleteBefore(millis: Long)
}
