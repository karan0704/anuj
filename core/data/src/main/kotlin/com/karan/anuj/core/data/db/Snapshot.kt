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
    val checklistItems: List<ChecklistItemEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val taskTags: List<TaskTagEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val occurrences: List<TaskOccurrenceEntity> = emptyList(),
    val changeHistory: List<ChangeHistoryEntity> = emptyList(),
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
    @Query("SELECT * FROM checklist_item") abstract suspend fun checklistItems(): List<ChecklistItemEntity>
    @Query("SELECT * FROM note") abstract suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM tag") abstract suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM task_tag") abstract suspend fun taskTags(): List<TaskTagEntity>
    @Query("SELECT * FROM attachment") abstract suspend fun attachments(): List<AttachmentEntity>
    @Query("SELECT * FROM task_occurrence") abstract suspend fun occurrences(): List<TaskOccurrenceEntity>
    @Query("SELECT * FROM change_history") abstract suspend fun changeHistory(): List<ChangeHistoryEntity>

    @Insert abstract suspend fun insertTasks(rows: List<TaskEntity>)
    @Insert abstract suspend fun insertChecklistItems(rows: List<ChecklistItemEntity>)
    @Insert abstract suspend fun insertNotes(rows: List<NoteEntity>)
    @Insert abstract suspend fun insertTags(rows: List<TagEntity>)
    @Insert abstract suspend fun insertTaskTags(rows: List<TaskTagEntity>)
    @Insert abstract suspend fun insertAttachments(rows: List<AttachmentEntity>)
    @Insert abstract suspend fun insertOccurrences(rows: List<TaskOccurrenceEntity>)
    @Insert abstract suspend fun insertChangeHistory(rows: List<ChangeHistoryEntity>)

    @Query("DELETE FROM task") abstract suspend fun clearTasks()
    @Query("DELETE FROM tag") abstract suspend fun clearTags()
    @Query("DELETE FROM change_history") abstract suspend fun clearChangeHistory()

    /** All tables are read inside one transaction so the copy is of a single moment. */
    @Transaction
    open suspend fun read(schemaVersion: Int): DatabaseSnapshot = DatabaseSnapshot(
        schemaVersion = schemaVersion,
        tasks = tasks(),
        checklistItems = checklistItems(),
        notes = notes(),
        tags = tags(),
        taskTags = taskTags(),
        attachments = attachments(),
        occurrences = occurrences(),
        changeHistory = changeHistory(),
    )

    /**
     * Empties the database and fills it from [snapshot] as one step: if any
     * row fails to insert, nothing is changed and the old data is still there.
     *
     * Deleting tasks and tags removes everything hanging off them (checklist,
     * notes, photos, rounds, tag links) through the foreign keys.
     */
    @Transaction
    open suspend fun replaceWith(snapshot: DatabaseSnapshot) {
        clearTasks()
        clearTags()
        clearChangeHistory()

        insertTags(snapshot.tags)
        insertTasks(snapshot.tasks)
        insertChecklistItems(snapshot.checklistItems)
        insertNotes(snapshot.notes)
        insertTaskTags(snapshot.taskTags)
        insertAttachments(snapshot.attachments)
        insertOccurrences(snapshot.occurrences)
        insertChangeHistory(snapshot.changeHistory)
    }
}
