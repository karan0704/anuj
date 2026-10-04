package com.karan.anuj.core.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** A circle on the ground, with what the user has decided it is. */
@Serializable
@Entity(tableName = "place")
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val kind: String,
    val isHome: Boolean = false,
    @Embedded val stamps: StampColumns,
)

/**
 * One stay at a place. Like a finished round of a routine it is a fact with
 * its own two moments, so it carries those instead of the record-keeping columns.
 */
@Serializable
@Entity(
    tableName = "place_visit",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index("placeId"), Index("arrivedAt")],
)
data class PlaceVisitEntity(
    @PrimaryKey val id: String,
    val placeId: String,
    val arrivedAt: Long,
    val leftAt: Long? = null,
)

/** A task tied to a place. A task has at most one, so the task is the key; it goes when either end goes. */
@Serializable
@Entity(
    tableName = "task_place",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index("placeId")],
)
data class TaskPlaceEntity(
    @PrimaryKey val taskId: String,
    val placeId: String,
    val moment: String,
)

@Dao
interface PlaceDao {
    @Query("SELECT * FROM place WHERE kind != 'SEEN' ORDER BY isHome DESC, name")
    fun observeShown(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM place")
    suspend fun getAll(): List<PlaceEntity>

    @Query("SELECT * FROM place WHERE id = :id")
    suspend fun get(id: String): PlaceEntity?

    @Upsert
    suspend fun upsert(place: PlaceEntity)

    @Query("DELETE FROM place WHERE id = :id")
    suspend fun delete(id: String)

    @Upsert
    suspend fun upsertVisit(visit: PlaceVisitEntity)

    @Query("SELECT * FROM place_visit WHERE placeId = :placeId AND leftAt IS NULL ORDER BY arrivedAt DESC LIMIT 1")
    suspend fun openVisit(placeId: String): PlaceVisitEntity?

    @Query("SELECT COUNT(*) FROM place_visit WHERE placeId = :placeId")
    suspend fun countVisits(placeId: String): Int

    /** A stay still under way is never removed, however long ago it began. */
    @Query("DELETE FROM place_visit WHERE leftAt IS NOT NULL AND leftAt < :millis")
    suspend fun deleteVisitsBefore(millis: Long)

    @Query("SELECT * FROM task_place WHERE taskId = :taskId")
    fun observeTie(taskId: String): Flow<TaskPlaceEntity?>

    @Upsert
    suspend fun upsertTie(tie: TaskPlaceEntity)

    @Query("DELETE FROM task_place WHERE taskId = :taskId")
    suspend fun deleteTie(taskId: String)

    @Query("SELECT * FROM task_place WHERE placeId = :placeId")
    suspend fun tiesOf(placeId: String): List<TaskPlaceEntity>
}
