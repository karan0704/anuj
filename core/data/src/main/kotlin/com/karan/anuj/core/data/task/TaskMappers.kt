package com.karan.anuj.core.data.task

import com.karan.anuj.core.data.db.AttachmentEntity
import com.karan.anuj.core.data.db.ChecklistItemEntity
import com.karan.anuj.core.data.db.NoteEntity
import com.karan.anuj.core.data.db.StampColumns
import com.karan.anuj.core.data.db.TagEntity
import com.karan.anuj.core.data.db.TaskEntity
import com.karan.anuj.core.data.db.TaskOccurrenceEntity
import com.karan.anuj.core.data.db.TaskTagEntity
import com.karan.anuj.core.domain.task.Attachment
import com.karan.anuj.core.domain.task.CarryOverCodec
import com.karan.anuj.core.domain.task.ChecklistItem
import com.karan.anuj.core.domain.task.Energy
import com.karan.anuj.core.domain.task.Note
import com.karan.anuj.core.domain.task.OccurrenceOutcome
import com.karan.anuj.core.domain.task.Priority
import com.karan.anuj.core.domain.task.RepetitionCodec
import com.karan.anuj.core.domain.task.Tag
import com.karan.anuj.core.domain.task.TagId
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TaskOccurrence
import com.karan.anuj.core.domain.task.WeekdaysCodec
import java.time.LocalDate
import java.time.LocalTime

/**
 * Conversions between the rows the database stores and the values the rest
 * of the app works with. A stored name this version does not know (written
 * by a newer version) falls back to the neutral value instead of failing.
 */

internal fun TaskEntity.toDomain(tagIds: Set<TagId>): Task = Task(
    id = TaskId(id),
    parentId = parentId?.let(::TaskId),
    name = name,
    description = description,
    dueDate = dueDay?.let(LocalDate::ofEpochDay),
    dueTime = dueMinute?.let { LocalTime.ofSecondOfDay(it.coerceIn(0, MINUTES_PER_DAY - 1) * 60L) },
    repetition = RepetitionCodec.decode(repetition),
    daysOff = WeekdaysCodec.decode(daysOff),
    priority = Priority.entries.firstOrNull { it.name == priority } ?: Priority.NONE,
    energy = Energy.entries.firstOrNull { it.name == energy },
    estimatedMinutes = estimatedMinutes,
    carryOver = CarryOverCodec.decode(carryOver),
    carryCount = carryCount,
    tagIds = tagIds,
    completedAt = completedAt,
    missedAt = missedAt,
    stamps = stamps.toDomain(),
)

internal fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id.value,
    parentId = parentId?.value,
    name = name,
    description = description,
    dueDay = dueDate?.toEpochDay(),
    dueMinute = dueTime?.let { it.hour * 60 + it.minute },
    repetition = RepetitionCodec.encode(repetition),
    daysOff = WeekdaysCodec.encode(daysOff),
    priority = priority.name,
    energy = energy?.name,
    estimatedMinutes = estimatedMinutes,
    carryOver = CarryOverCodec.encode(carryOver),
    carryCount = carryCount,
    completedAt = completedAt,
    missedAt = missedAt,
    stamps = StampColumns.from(stamps),
)

internal fun Task.toTagRows(): List<TaskTagEntity> = tagIds.map { TaskTagEntity(taskId = id.value, tagId = it.value) }

internal fun ChecklistItemEntity.toDomain() =
    ChecklistItem(id, TaskId(taskId), text, checked, position, stamps.toDomain())

internal fun ChecklistItem.toEntity() =
    ChecklistItemEntity(id, taskId.value, text, checked, position, StampColumns.from(stamps))

internal fun NoteEntity.toDomain() = Note(id, TaskId(taskId), text, stamps.toDomain())

internal fun Note.toEntity() = NoteEntity(id, taskId.value, text, StampColumns.from(stamps))

internal fun TagEntity.toDomain() = Tag(TagId(id), name, colorIndex, stamps.toDomain())

internal fun Tag.toEntity() = TagEntity(id.value, name, colorIndex, StampColumns.from(stamps))

internal fun AttachmentEntity.toDomain() = Attachment(id, TaskId(taskId), fileName, stamps.toDomain())

internal fun Attachment.toEntity() = AttachmentEntity(id, taskId.value, fileName, StampColumns.from(stamps))

internal fun TaskOccurrenceEntity.toDomain() = TaskOccurrence(
    id = id,
    taskId = TaskId(taskId),
    date = LocalDate.ofEpochDay(day),
    outcome = OccurrenceOutcome.entries.firstOrNull { it.name == outcome } ?: OccurrenceOutcome.MISSED,
    at = at,
)

internal fun TaskOccurrence.toEntity() = TaskOccurrenceEntity(id, taskId.value, date.toEpochDay(), outcome.name, at)

private const val MINUTES_PER_DAY = 24 * 60
