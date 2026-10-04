package com.karan.anuj.core.domain.task

import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.time.TimeSource
import java.time.LocalDate
import javax.inject.Inject

class CreateTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val ids: IdGenerator,
    private val time: TimeSource,
) {
    /**
     * @param today used as the first due day of a repeating task that was
     * given no date, since a rhythm needs a day to start from
     * @return the new task, or null when the name is blank
     */
    suspend operator fun invoke(draft: TaskDraft, today: LocalDate): Task? {
        val name = draft.name.trim()
        if (name.isEmpty()) return null

        val task = Task(
            id = TaskId(ids.newId()),
            parentId = draft.parentId,
            name = name,
            description = draft.description.trim(),
            dueDate = draft.dueDate ?: today.takeIf { draft.repetition != null },
            dueTime = draft.dueTime,
            repetition = draft.repetition,
            daysOff = draft.daysOff,
            priority = draft.priority,
            energy = draft.energy,
            estimatedMinutes = draft.estimatedMinutes,
            carryOver = draft.carryOver,
            tagIds = draft.tagIds,
            stamps = RecordStamps.created(time.nowMillis()),
        )
        tasks.save(listOf(task))
        return task
    }
}

class UpdateTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val editor: TaskEditor,
) {
    /**
     * Applies [change] to the task as it is stored right now, not to a copy
     * a screen has been holding, so two quick edits cannot overwrite each
     * other. A blank name is refused and the old one kept.
     */
    suspend operator fun invoke(id: TaskId, change: (Task) -> Task) {
        val before = tasks.get(id) ?: return
        val changed = change(before)
        val after = changed.copy(
            id = before.id,
            name = changed.name.trim().ifEmpty { before.name },
            /** A repeating task always needs a day; removing the date from one would stop it silently. */
            dueDate = changed.dueDate ?: before.dueDate.takeIf { changed.repetition != null },
        )
        editor.apply(listOf(before to after))
    }
}

/** Everything a completion changed, so "Undo" can put it back exactly. */
data class CompletionUndo(
    val tasks: List<Task>,
    val occurrenceId: String?,
)

class CompleteTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val editor: TaskEditor,
    private val ids: IdGenerator,
) {
    /**
     * Marks a task done.
     *
     * A one-off task is closed together with every open sub-task beneath it:
     * ticking the parent means the whole thing is finished.
     *
     * A repeating task is never closed. The round is recorded as done and the
     * task moves to its next due day with its steps reopened.
     *
     * @return what to hand to [UndoCompletionUseCase], or null if the task
     * does not exist or is already closed
     */
    suspend operator fun invoke(id: TaskId, today: LocalDate): CompletionUndo? {
        val subtree = tasks.getSubtree(id).filterNot { it.stamps.isDeleted }
        val task = subtree.firstOrNull { it.id == id } ?: return null
        if (!task.isOpen) return null
        val now = editor.now()

        val rule = task.repetition
        val due = task.dueDate
        if (rule != null && due != null) {
            val occurrence = TaskOccurrence(ids.newId(), task.id, due, OccurrenceOutcome.DONE, now)
            val next = RepetitionCalculator.nextAfter(rule, due, today, task.daysOff)
            val before = editor.startNewRound(task, subtree, next, now)
            tasks.addOccurrence(occurrence)
            return CompletionUndo(before, occurrence.id)
        }

        val closing = subtree.filter { it.isOpen }
        editor.apply(closing.map { it to it.copy(completedAt = now) }, now)
        return CompletionUndo(tasks = closing, occurrenceId = null)
    }
}

class UndoCompletionUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val editor: TaskEditor,
) {
    suspend operator fun invoke(undo: CompletionUndo) {
        val now = editor.now()
        val changes = undo.tasks.mapNotNull { before -> tasks.get(before.id)?.let { current -> current to before } }
        editor.apply(changes, now)
        undo.occurrenceId?.let { tasks.removeOccurrence(it) }
    }
}

/** Puts a finished or missed one-off task back on the list. */
class ReopenTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val editor: TaskEditor,
) {
    suspend operator fun invoke(id: TaskId) {
        val task = tasks.get(id) ?: return
        editor.apply(listOf(task to task.copy(completedAt = null, missedAt = null)))
    }
}

class MoveTaskToDayUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val editor: TaskEditor,
) {
    /**
     * @param date the new day, or null to take the date away
     * @param countsAsCarry true when the task is being pushed back because it
     * was not done, which is what the carry count keeps track of
     */
    suspend operator fun invoke(id: TaskId, date: LocalDate?, countsAsCarry: Boolean) {
        val task = tasks.get(id) ?: return
        /** A repeating task cannot be left without a day, so taking the date away is ignored for it. */
        if (date == null && task.repetition != null) return
        editor.apply(
            listOf(
                task to task.copy(
                    dueDate = date,
                    missedAt = null,
                    carryCount = if (countsAsCarry) task.carryCount + 1 else task.carryCount,
                ),
            ),
        )
    }
}

class DuplicateTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val ids: IdGenerator,
    private val time: TimeSource,
) {
    /**
     * Copies a task with every step beneath it. The copy starts fresh:
     * nothing done, never carried.
     *
     * @return the id of the copy, or null if the task does not exist
     */
    suspend operator fun invoke(id: TaskId, nameOfCopy: (String) -> String): TaskId? {
        val subtree = tasks.getSubtree(id).filterNot { it.stamps.isDeleted }
        if (subtree.none { it.id == id }) return null
        val now = time.nowMillis()
        val newIds = subtree.associate { it.id to TaskId(ids.newId()) }

        val copies = orderedParentsFirst(subtree, rootId = id).map { original ->
            original.copy(
                id = newIds.getValue(original.id),
                parentId = if (original.id == id) original.parentId else original.parentId?.let(newIds::get),
                name = if (original.id == id) nameOfCopy(original.name) else original.name,
                carryCount = 0,
                completedAt = null,
                missedAt = null,
                stamps = RecordStamps.created(now),
            )
        }
        tasks.save(copies)
        return newIds.getValue(id)
    }
}

/** Storage needs a parent saved before its children; this lists a subtree in that order. */
internal fun orderedParentsFirst(subtree: List<Task>, rootId: TaskId): List<Task> {
    val childrenOf = subtree.groupBy { it.parentId }
    val ordered = ArrayList<Task>(subtree.size)
    val queue = ArrayDeque(subtree.filter { it.id == rootId })
    val seen = HashSet<TaskId>()
    while (queue.isNotEmpty()) {
        val task = queue.removeFirst()
        if (!seen.add(task.id)) continue
        ordered += task
        queue.addAll(childrenOf[task.id].orEmpty())
    }
    return ordered
}
