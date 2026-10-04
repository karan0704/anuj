package com.karan.anuj.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The condition for "still something to do", shared by the reads below. */
private const val OPEN = "deletedAt IS NULL AND completedAt IS NULL AND missedAt IS NULL"

@Dao
abstract class TaskDao {

    @Query("SELECT * FROM task WHERE $OPEN")
    abstract fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE $OPEN")
    abstract suspend fun getOpen(): List<TaskEntity>

    @Query("SELECT * FROM task WHERE deletedAt IS NULL")
    abstract fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE deletedAt IS NULL AND completedAt >= :fromMillis AND completedAt < :toMillis")
    abstract fun observeCompletedBetween(fromMillis: Long, toMillis: Long): Flow<List<TaskEntity>>

    @Query(
        "SELECT parentId AS parentId, COUNT(*) AS total, " +
            "SUM(CASE WHEN completedAt IS NOT NULL THEN 1 ELSE 0 END) AS done " +
            "FROM task WHERE deletedAt IS NULL AND parentId IS NOT NULL GROUP BY parentId",
    )
    abstract fun observeChildProgress(): Flow<List<ChildProgressRow>>

    @Query("SELECT * FROM task WHERE id = :id")
    abstract fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM task WHERE parentId = :parentId AND deletedAt IS NULL")
    abstract fun observeChildren(parentId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE deletedAt IS NOT NULL")
    abstract fun observeTrash(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE deletedAt IS NOT NULL")
    abstract suspend fun getTrash(): List<TaskEntity>

    @Query("SELECT * FROM task WHERE id = :id")
    abstract suspend fun getById(id: String): TaskEntity?

    /**
     * The task and everything beneath it, found by walking down the parent
     * links inside the database. UNION (not UNION ALL) drops ids already
     * visited, so a parent loop in damaged data ends instead of running forever.
     */
    @Query(
        "WITH RECURSIVE subtree(id) AS (" +
            "SELECT :id UNION SELECT task.id FROM task JOIN subtree ON task.parentId = subtree.id" +
            ") SELECT * FROM task WHERE id IN (SELECT id FROM subtree)",
    )
    abstract suspend fun getSubtree(id: String): List<TaskEntity>

    /**
     * Tasks matching a full-text query in their own text or a note. A step
     * is a task, so it is found by its own name. Open tasks come first, then
     * the most recently changed.
     */
    @Query(
        "SELECT * FROM task WHERE deletedAt IS NULL AND id IN (" +
            "SELECT task.id FROM task JOIN task_fts ON task.rowid = task_fts.rowid WHERE task_fts MATCH :match " +
            "UNION SELECT note.taskId FROM note JOIN note_fts ON note.rowid = note_fts.rowid " +
            "WHERE note_fts MATCH :match AND note.deletedAt IS NULL" +
            ") ORDER BY (completedAt IS NOT NULL OR missedAt IS NOT NULL), updatedAt DESC LIMIT :limit",
    )
    abstract suspend fun search(match: String, limit: Int): List<TaskEntity>

    @Query("SELECT name FROM task WHERE deletedAt IS NULL GROUP BY name ORDER BY MAX(createdAt) DESC LIMIT :limit")
    abstract suspend fun recentNames(limit: Int): List<String>

    /** Use [save] instead, which also keeps the tag rows in step. */
    @Upsert
    abstract suspend fun upsertRows(tasks: List<TaskEntity>)

    @Query("DELETE FROM task WHERE id IN (:ids)")
    abstract suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM task_tag")
    abstract fun observeTags(): Flow<List<TaskTagEntity>>

    @Query("SELECT * FROM task_tag WHERE taskId IN (:taskIds)")
    abstract suspend fun getTagsFor(taskIds: List<String>): List<TaskTagEntity>

    @Query("DELETE FROM task_tag WHERE taskId IN (:taskIds)")
    abstract suspend fun deleteTagsFor(taskIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTags(refs: List<TaskTagEntity>)

    /** Writes tasks and makes each one's tag rows match exactly what it now carries, as one step. */
    @Transaction
    open suspend fun save(tasks: List<TaskEntity>, tags: List<TaskTagEntity>) {
        upsertRows(tasks)
        deleteTagsFor(tasks.map { it.id })
        insertTags(tags)
    }

    @Query("SELECT * FROM task_occurrence WHERE at >= :fromMillis AND at < :toMillis")
    abstract fun observeOccurrencesBetween(fromMillis: Long, toMillis: Long): Flow<List<TaskOccurrenceEntity>>

    @Insert
    abstract suspend fun insertOccurrence(occurrence: TaskOccurrenceEntity)

    @Query("DELETE FROM task_occurrence WHERE id = :id")
    abstract suspend fun deleteOccurrence(id: String)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM note WHERE taskId = :taskId AND deletedAt IS NULL")
    fun observeFor(taskId: String): Flow<List<NoteEntity>>

    @Upsert
    suspend fun upsert(note: NoteEntity)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tag WHERE deletedAt IS NULL ORDER BY createdAt, name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag WHERE deletedAt IS NULL ORDER BY createdAt, name")
    suspend fun getAll(): List<TagEntity>

    @Upsert
    suspend fun upsert(tags: List<TagEntity>)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachment WHERE taskId = :taskId AND deletedAt IS NULL")
    fun observeFor(taskId: String): Flow<List<AttachmentEntity>>

    /** Includes removed photos, because their files still need deleting when the task is purged. */
    @Query("SELECT * FROM attachment WHERE taskId IN (:taskIds)")
    suspend fun getFor(taskIds: List<String>): List<AttachmentEntity>

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)
}
