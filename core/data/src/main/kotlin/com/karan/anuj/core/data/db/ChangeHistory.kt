package com.karan.anuj.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * One row per changed field. History rows are written once and never
 * edited, so they carry only the time of the change instead of the full
 * created / updated / deleted set. Old rows are removed only when the user
 * has chosen how long history is kept.
 *
 * The first index serves the history of one row, newest first; the second
 * serves the History screen and the clean-up, which both go by time alone.
 */
@Serializable
@Entity(
    tableName = "change_history",
    indices = [Index(value = ["tableName", "rowId", "changedAt"]), Index("changedAt")],
)
data class ChangeHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,
    val rowId: String,
    val field: String,
    val oldValue: String?,
    val newValue: String?,
    val changedAt: Long,
)

@Dao
interface ChangeHistoryDao {
    @Insert
    suspend fun insertAll(rows: List<ChangeHistoryEntity>)

    @Query(
        "SELECT * FROM change_history WHERE tableName = :table AND rowId = :rowId " +
            "ORDER BY changedAt DESC, id DESC",
    )
    fun observeFor(table: String, rowId: String): Flow<List<ChangeHistoryEntity>>

    @Query("SELECT * FROM change_history ORDER BY changedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ChangeHistoryEntity>>

    @Query("DELETE FROM change_history WHERE changedAt < :millis")
    suspend fun deleteBefore(millis: Long)
}
