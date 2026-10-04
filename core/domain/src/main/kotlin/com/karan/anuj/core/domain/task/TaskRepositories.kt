package com.karan.anuj.core.domain.task

import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

/** Supplies a new unique id for a record. Behind an interface so tests get predictable ids. */
fun interface IdGenerator {
    fun newId(): String
}

/**
 * Storage for tasks. Deleted (trashed) tasks are left out of every read
 * unless the function says otherwise.
 */
interface TaskRepository {

    /** Tasks that are neither done nor missed. */
    fun observeOpen(): Flow<List<Task>>

    /** Every task that is not in the trash, finished ones included. */
    fun observeAll(): Flow<List<Task>>

    /** Tasks completed at or after [fromMillis] and before [toMillis]. */
    fun observeCompletedBetween(fromMillis: Long, toMillis: Long): Flow<List<Task>>

    /** Sub-task counts for every task that has sub-tasks. */
    fun observeChildProgress(): Flow<Map<TaskId, ChildProgress>>

    /** One task, also while it is in the trash; null once it no longer exists. */
    fun observeTask(id: TaskId): Flow<Task?>

    fun observeChildren(parentId: TaskId): Flow<List<Task>>

    /** Tasks in the trash. */
    fun observeTrash(): Flow<List<Task>>

    /** One task, also if it is in the trash. */
    suspend fun get(id: TaskId): Task?

    suspend fun getOpen(): List<Task>

    /** The task and everything beneath it, including anything in the trash. */
    suspend fun getSubtree(id: TaskId): List<Task>

    suspend fun getTrash(): List<Task>

    /** Inserts new tasks and updates existing ones, tags included. A parent must come before its children. */
    suspend fun save(tasks: List<Task>)

    /** Removes tasks for good, with their notes, photo records and history of rounds. */
    suspend fun purge(ids: List<TaskId>)

    /** Tasks whose name, description or notes match, best candidates first. */
    suspend fun search(query: SearchQuery): List<Task>

    /** The most recently used distinct task names, newest first. */
    suspend fun recentNames(limit: Int): List<String>

    /** Rounds of repeating tasks finished or missed at or after [fromMillis] and before [toMillis]. */
    fun observeOccurrencesBetween(fromMillis: Long, toMillis: Long): Flow<List<TaskOccurrence>>

    suspend fun addOccurrence(occurrence: TaskOccurrence)

    suspend fun removeOccurrence(id: String)
}

interface NoteRepository {
    fun observeFor(taskId: TaskId): Flow<List<Note>>
    suspend fun save(note: Note)
}

interface TagRepository {
    fun observeAll(): Flow<List<Tag>>
    suspend fun getAll(): List<Tag>
    suspend fun save(tags: List<Tag>)
}

interface AttachmentRepository {
    fun observeFor(taskId: TaskId): Flow<List<Attachment>>
    suspend fun getFor(taskIds: List<TaskId>): List<Attachment>
    suspend fun save(attachment: Attachment)
}

/**
 * The photo files themselves, kept in the app's private storage. Addresses
 * are passed as plain text so this module stays free of Android types.
 */
interface AttachmentFileStore {

    /** Copies the picture at [sourceUri] into the app's own storage and returns the new file's name. */
    suspend fun importFrom(sourceUri: String): String

    /** Reserves a new empty file for the camera to write into and returns its name. */
    suspend fun reserveForCamera(): String

    /** The address another app (the camera) can write a reserved file through. */
    fun shareableUriFor(fileName: String): String

    /** Where the file is on disk, for showing it. */
    fun pathOf(fileName: String): String

    suspend fun delete(fileNames: List<String>)

    /**
     * Sets files aside instead of deleting them: they leave the photo folder,
     * so nothing shows or backs them up any more, and are remembered with the
     * moment they were set aside.
     */
    suspend fun setAside(fileNames: List<String>)

    /** Deletes the files that were set aside before [millis]. */
    suspend fun deleteSetAsideBefore(millis: Long)
}

/**
 * The clock times behind the "Morning", "Afternoon", "Evening" and "Night"
 * chips, so a part of the day means what it means in the user's own day.
 */
data class DayParts(
    val morning: LocalTime = LocalTime.of(8, 0),
    val afternoon: LocalTime = LocalTime.of(13, 0),
    val evening: LocalTime = LocalTime.of(18, 0),
    val night: LocalTime = LocalTime.of(21, 0),
)

data class TaskPreferences(
    val defaultCarryOver: CarryOverRule = CarryOverRule.NextDay,
    /** After this many carries the app asks what to do with the task. */
    val carryLimit: Int = 3,
    val dayParts: DayParts = DayParts(),
    /** The lengths offered as "Time needed" chips on a task, in minutes. */
    val estimateChoices: List<Int> = DEFAULT_ESTIMATE_CHOICES,
) {
    companion object {
        val DEFAULT_ESTIMATE_CHOICES: List<Int> = listOf(5, 15, 30, 60, 120)

        /** Every length that can be switched on as a "Time needed" chip, in minutes. */
        val ESTIMATE_CANDIDATES: List<Int> = listOf(2, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240)
    }
}

interface TaskPreferencesRepository {
    val preferences: Flow<TaskPreferences>
    suspend fun setDefaultCarryOver(rule: CarryOverRule)
    suspend fun setCarryLimit(limit: Int)
    suspend fun setDayParts(parts: DayParts)
    suspend fun setEstimateChoices(minutes: List<Int>)
}
