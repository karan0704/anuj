package com.karan.anuj.core.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * One reminder. [taskId] is set for a task's reminder and null for one that
 * stands on its own; a task's reminders go when the task is removed for good.
 *
 * [schedule] is the text form written by `ReminderScheduleCodec`. The five
 * columns from [lastOccurrenceAt] to [answeredAt] are the engine's own
 * bookkeeping (instants in epoch milliseconds), not something the user set.
 *
 * Indexes: [taskId] for a task's reminders, [deletedAt] because every read
 * filters on it.
 */
@Serializable
@Entity(
    tableName = "reminder",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index("taskId"), Index("deletedAt")],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val taskId: String? = null,
    val title: String = "",
    val schedule: String,
    val category: String = "TASK",
    val style: String = "NOTIFICATION",
    val nagEveryMinutes: Int? = null,
    val nagTimes: Int = 0,
    val toneUri: String? = null,
    val enabled: Boolean = true,
    val lastOccurrenceAt: Long? = null,
    val lastFiredAt: Long? = null,
    val nagsSent: Int = 0,
    val snoozedUntil: Long? = null,
    val answeredAt: Long? = null,
    @Embedded val stamps: StampColumns,
)

/**
 * One line of the reminder log: shown, repeated, held back, snoozed, done.
 * Like the rounds of a repeating task, a line is a fact that happened at
 * [at] and is never edited, so it carries that one instant instead of the
 * three record-keeping columns.
 *
 * It deliberately has no foreign key: the log still reads after the task
 * or reminder is gone, which is why the [title] is copied in.
 */
@Serializable
@Entity(tableName = "reminder_event", indices = [Index("at"), Index("reminderId")])
data class ReminderEventEntity(
    @PrimaryKey val id: String,
    val reminderId: String? = null,
    val taskId: String? = null,
    val title: String = "",
    val kind: String,
    val at: Long,
    val minutes: Int? = null,
    val reason: String? = null,
)

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminder WHERE taskId = :taskId AND deletedAt IS NULL ORDER BY createdAt")
    fun observeFor(taskId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE taskId IS NULL AND deletedAt IS NULL ORDER BY createdAt")
    fun observeStanding(): Flow<List<ReminderEntity>>

    /**
     * Its value is unimportant; it is here because Room runs it again
     * whenever the reminder or the task table changes, and each of those
     * changes can move the next alarm.
     */
    @Query("SELECT (SELECT COUNT(*) FROM reminder) + (SELECT COUNT(*) FROM task)")
    fun observeChangeTick(): Flow<Int>

    @Query("SELECT * FROM reminder_event WHERE reminderId = :reminderId ORDER BY at")
    fun observeEventsFor(reminderId: String): Flow<List<ReminderEventEntity>>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Query("SELECT * FROM reminder WHERE deletedAt IS NULL")
    suspend fun getLive(): List<ReminderEntity>

    @Query("SELECT DISTINCT taskId FROM reminder WHERE taskId IS NOT NULL")
    suspend fun taskIdsEverReminded(): List<String>

    @Upsert
    suspend fun upsert(rows: List<ReminderEntity>)

    @Insert
    suspend fun insertEvent(row: ReminderEventEntity)

    @Query("SELECT * FROM reminder_event WHERE at >= :fromMillis ORDER BY at")
    suspend fun eventsSince(fromMillis: Long): List<ReminderEventEntity>

    @Query("SELECT MAX(at) FROM reminder_event WHERE kind = :kind")
    suspend fun lastEventAt(kind: String): Long?

    @Query("SELECT * FROM reminder_event ORDER BY at DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int): Flow<List<ReminderEventEntity>>

    @Query("DELETE FROM reminder_event WHERE at < :millis")
    suspend fun deleteEventsBefore(millis: Long)
}
