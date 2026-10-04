package com.karan.anuj.core.domain.task

import com.karan.anuj.core.domain.record.RecordStamps
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@JvmInline
value class TaskId(val value: String)

@JvmInline
value class TagId(val value: String)

enum class Priority { NONE, LOW, MEDIUM, HIGH }

/** How much effort a task takes, so one can be picked to match how the user feels. */
enum class Energy { LOW, MEDIUM, HIGH }

/**
 * A task, at any depth of the tree: [parentId] is null for a top-level task
 * and points at the containing task otherwise.
 *
 * [dueDate] and [dueTime] are calendar values, not instants. A task due "on
 * the 5th at 6 pm" stays on the 5th at 6 pm wherever the phone travels, which
 * an instant stored in UTC would not. Moments that did happen
 * ([completedAt], [missedAt], [stamps]) are instants in epoch milliseconds.
 *
 * @property carryOver null means "use the rule of the nearest parent that has
 * one, or the app default"
 * @property carryCount how many times the task has been moved to a later day
 * because it was not done
 * @property missedAt set when the task's day passed and its rule said not to
 * carry it; a missed task is closed without being done
 */
data class Task(
    val id: TaskId,
    val parentId: TaskId? = null,
    val name: String,
    val description: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val repetition: Repetition? = null,
    val daysOff: Set<DayOfWeek> = emptySet(),
    val priority: Priority = Priority.NONE,
    val energy: Energy? = null,
    val estimatedMinutes: Int? = null,
    val carryOver: CarryOverRule? = null,
    val carryCount: Int = 0,
    val tagIds: Set<TagId> = emptySet(),
    val completedAt: Long? = null,
    val missedAt: Long? = null,
    val stamps: RecordStamps,
) {
    val isDone: Boolean get() = completedAt != null
    val isMissed: Boolean get() = missedAt != null

    /** Still something to do: not done, not missed, not in the trash. */
    val isOpen: Boolean get() = !isDone && !isMissed && !stamps.isDeleted
}

/** What the user supplies to create a task. Only [name] has no default. */
data class TaskDraft(
    val name: String,
    val parentId: TaskId? = null,
    val description: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val repetition: Repetition? = null,
    val daysOff: Set<DayOfWeek> = emptySet(),
    val priority: Priority = Priority.NONE,
    val energy: Energy? = null,
    val estimatedMinutes: Int? = null,
    val carryOver: CarryOverRule? = null,
    val tagIds: Set<TagId> = emptySet(),
)

/** One line of a task's checklist: lighter than a sub-task, just text and a tick. */
data class ChecklistItem(
    val id: String,
    val taskId: TaskId,
    val text: String,
    val checked: Boolean = false,
    val position: Int,
    val stamps: RecordStamps,
)

/** A timestamped note added to a task. */
data class Note(
    val id: String,
    val taskId: TaskId,
    val text: String,
    val stamps: RecordStamps,
)

/**
 * A label that groups tasks across the tree.
 *
 * @property colorIndex position in the app's fixed tag palette, so a tag is
 * given a colour by tapping a swatch rather than describing one
 */
data class Tag(
    val id: TagId,
    val name: String,
    val colorIndex: Int,
    val stamps: RecordStamps,
)

/** A photo kept with a task. [fileName] is the file inside the app's private attachment folder. */
data class Attachment(
    val id: String,
    val taskId: TaskId,
    val fileName: String,
    val stamps: RecordStamps,
)

enum class OccurrenceOutcome { DONE, MISSED }

/**
 * One past round of a repeating task. The task row itself always describes
 * the next round, so the rounds already finished or missed are kept here.
 */
data class TaskOccurrence(
    val id: String,
    val taskId: TaskId,
    val date: LocalDate,
    val outcome: OccurrenceOutcome,
    val at: Long,
)

/** How many direct sub-tasks a task has, and how many of them are done. */
data class ChildProgress(val total: Int, val done: Int)
