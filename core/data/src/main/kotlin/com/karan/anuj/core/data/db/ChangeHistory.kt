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
 * One row per changed field. History rows are written once and never edited
 * or deleted, so they carry only the time of the change instead of the full
 * created / updated / deleted set.
 *
 * The index matches the only read this table serves: the history of one row,
 * newest first.
 */
@Serializable
@Entity(
    tableName = "change_history",
    indices = [Index(value = ["tableName", "rowId", "changedAt"])],
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
}
