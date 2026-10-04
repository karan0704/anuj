package com.karan.anuj.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/**
 * Every row of every table, in the shape a backup file stores them.
 *
 * Each list has a default so a backup written before a table existed still
 * reads: the missing table simply comes back empty.
 *
 * @property schemaVersion the database version the rows were read from
 */
@Serializable
data class DatabaseSnapshot(
    val schemaVersion: Int,
    val tasks: List<TaskEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val taskTags: List<TaskTagEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val occurrences: List<TaskOccurrenceEntity> = emptyList(),
    val changeHistory: List<ChangeHistoryEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val reminderEvents: List<ReminderEventEntity> = emptyList(),
    val places: List<PlaceEntity> = emptyList(),
    val placeVisits: List<PlaceVisitEntity> = emptyList(),
    val taskPlaces: List<TaskPlaceEntity> = emptyList(),
)

/**
 * How a [DatabaseSnapshot] is written to and read from a backup file.
 *
 * Reading is lenient on purpose: a column added by a newer version is
 * skipped, and a column this version added since the backup was made takes
 * its default, so an older backup keeps restoring as the app grows.
 */
@OptIn(ExperimentalSerializationApi::class)
object SnapshotFormat {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** Writes straight to the stream, so a large database is never held in memory as one string. */
    fun write(snapshot: DatabaseSnapshot, output: OutputStream) = json.encodeToStream(snapshot, output)

    fun read(input: InputStream): DatabaseSnapshot = json.decodeFromStream(input)
}

/** Reads and replaces the whole database at once, for backup and restore. */
@Dao
abstract class SnapshotDao {

    @Query("SELECT * FROM task") abstract suspend fun tasks(): List<TaskEntity>
    @Query("SELECT * FROM note") abstract suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM tag") abstract suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM task_tag") abstract suspend fun taskTags(): List<TaskTagEntity>
    @Query("SELECT * FROM attachment") abstract suspend fun attachments(): List<AttachmentEntity>
    @Query("SELECT * FROM task_occurrence") abstract suspend fun occurrences(): List<TaskOccurrenceEntity>
    @Query("SELECT * FROM change_history") abstract suspend fun changeHistory(): List<ChangeHistoryEntity>
    @Query("SELECT * FROM reminder") abstract suspend fun reminders(): List<ReminderEntity>
    @Query("SELECT * FROM reminder_event") abstract suspend fun reminderEvents(): List<ReminderEventEntity>
    @Query("SELECT * FROM place") abstract suspend fun places(): List<PlaceEntity>
    @Query("SELECT * FROM place_visit") abstract suspend fun placeVisits(): List<PlaceVisitEntity>
    @Query("SELECT * FROM task_place") abstract suspend fun taskPlaces(): List<TaskPlaceEntity>

    @Insert abstract suspend fun insertTasks(rows: List<TaskEntity>)
    @Insert abstract suspend fun insertNotes(rows: List<NoteEntity>)
    @Insert abstract suspend fun insertTags(rows: List<TagEntity>)
    @Insert abstract suspend fun insertTaskTags(rows: List<TaskTagEntity>)
    @Insert abstract suspend fun insertAttachments(rows: List<AttachmentEntity>)
    @Insert abstract suspend fun insertOccurrences(rows: List<TaskOccurrenceEntity>)
    @Insert abstract suspend fun insertChangeHistory(rows: List<ChangeHistoryEntity>)
    @Insert abstract suspend fun insertReminders(rows: List<ReminderEntity>)
    @Insert abstract suspend fun insertReminderEvents(rows: List<ReminderEventEntity>)
    @Insert abstract suspend fun insertPlaces(rows: List<PlaceEntity>)
    @Insert abstract suspend fun insertPlaceVisits(rows: List<PlaceVisitEntity>)
    @Insert abstract suspend fun insertTaskPlaces(rows: List<TaskPlaceEntity>)

    @Query("DELETE FROM task") abstract suspend fun clearTasks()
    @Query("DELETE FROM tag") abstract suspend fun clearTags()
    @Query("DELETE FROM change_history") abstract suspend fun clearChangeHistory()
    @Query("DELETE FROM reminder") abstract suspend fun clearReminders()
    @Query("DELETE FROM reminder_event") abstract suspend fun clearReminderEvents()
    @Query("DELETE FROM place") abstract suspend fun clearPlaces()

    /** All tables are read inside one transaction so the copy is of a single moment. */
    @Transaction
    open suspend fun read(schemaVersion: Int): DatabaseSnapshot = DatabaseSnapshot(
        schemaVersion = schemaVersion,
        tasks = tasks(),
        notes = notes(),
        tags = tags(),
        taskTags = taskTags(),
        attachments = attachments(),
        occurrences = occurrences(),
        changeHistory = changeHistory(),
        reminders = reminders(),
        reminderEvents = reminderEvents(),
        places = places(),
        placeVisits = placeVisits(),
        taskPlaces = taskPlaces(),
    )

    /**
     * Empties the database and fills it from [snapshot] as one step: if any
     * row fails to insert, nothing is changed and the old data is still there.
     *
     * Deleting tasks and tags removes everything hanging off them (steps,
     * notes, photos, rounds, tag links, a task's reminders) through the
     * foreign keys. Reminders that stand on their own and the reminder log
     * hang off nothing, so they are cleared by name; so are places, which
     * take their visits and their ties to tasks with them.
     */
    @Transaction
    open suspend fun replaceWith(snapshot: DatabaseSnapshot) {
        clearTasks()
        clearTags()
        clearChangeHistory()
        clearReminders()
        clearReminderEvents()
        clearPlaces()

        insertTags(snapshot.tags)
        insertTasks(snapshot.tasks)
        insertNotes(snapshot.notes)
        insertTaskTags(snapshot.taskTags)
        insertAttachments(snapshot.attachments)
        insertOccurrences(snapshot.occurrences)
        insertChangeHistory(snapshot.changeHistory)
        insertReminders(snapshot.reminders)
        insertReminderEvents(snapshot.reminderEvents)
        insertPlaces(snapshot.places)
        insertPlaceVisits(snapshot.placeVisits)
        insertTaskPlaces(snapshot.taskPlaces)
    }
}
