package com.karan.anuj.core.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One row per task at any depth; [parentId] makes the tree.
 *
 * Calendar values are stored as numbers that mean the same in every time
 * zone: [dueDay] is days since 1 January 1970 and [dueMinute] is minutes
 * since midnight. Instants ([completedAt], [missedAt], the stamps) are epoch
 * milliseconds.
 *
 * Foreign keys are checked when a transaction ends rather than row by row,
 * so a batch may be written in any order (a restore from backup, a template
 * with its steps).
 *
 * Indexes: [parentId] for children and sub-task counts, [dueDay] and
 * [completedAt] for the day views, [deletedAt] because every read filters
 * on it.
 */
@Serializable
@Entity(
    tableName = "task",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index("parentId"), Index("dueDay"), Index("completedAt"), Index("deletedAt")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val parentId: String? = null,
    val name: String,
    val description: String = "",
    val dueDay: Long? = null,
    val dueMinute: Int? = null,
    val repetition: String? = null,
    val daysOff: Int = 0,
    val priority: String = "NONE",
    val energy: String? = null,
    val estimatedMinutes: Int? = null,
    val carryOver: String? = null,
    val carryCount: Int = 0,
    val completedAt: Long? = null,
    val missedAt: Long? = null,
    @Embedded val stamps: StampColumns,
)

@Serializable
@Entity(
    tableName = "note",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index("taskId")],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val text: String,
    @Embedded val stamps: StampColumns,
)

@Serializable
@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorIndex: Int = 0,
    @Embedded val stamps: StampColumns,
)

/**
 * Which tags a task carries. These rows are replaced as a set whenever the
 * task is saved and the change is recorded in the task's own history, so
 * they carry no created / updated / deleted columns of their own.
 */
@Serializable
@Entity(
    tableName = "task_tag",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index("tagId")],
)
data class TaskTagEntity(
    val taskId: String,
    val tagId: String,
)

@Serializable
@Entity(
    tableName = "attachment",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index("taskId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val fileName: String,
    @Embedded val stamps: StampColumns,
)

/**
 * A finished or missed round of a repeating task. Like the change history,
 * these rows are a log: written once, never edited, so they carry only the
 * time they were written. [at] is indexed for "what was done today".
 */
@Serializable
@Entity(
    tableName = "task_occurrence",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index("taskId"), Index("at")],
)
data class TaskOccurrenceEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val day: Long,
    val outcome: String,
    val at: Long,
)

/**
 * Full-text search indexes. Each mirrors the text columns of its content
 * table; Room keeps them in step with triggers, so nothing writes to these
 * tables directly.
 */
@Fts4(contentEntity = TaskEntity::class)
@Entity(tableName = "task_fts")
data class TaskFtsEntity(
    val name: String,
    val description: String,
)

@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "note_fts")
data class NoteFtsEntity(
    val text: String,
)

/** A row of the sub-task count query. */
data class ChildProgressRow(
    val parentId: String,
    val total: Int,
    val done: Int,
)
