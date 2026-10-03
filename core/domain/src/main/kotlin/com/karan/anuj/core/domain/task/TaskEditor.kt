package com.karan.anuj.core.domain.task

import com.karan.anuj.core.domain.history.ChangeHistoryRepository
import com.karan.anuj.core.domain.history.diffFields
import com.karan.anuj.core.domain.time.TimeSource
import java.time.LocalDate
import javax.inject.Inject

/** What a new round overwrote, kept so the step can be undone. */
data class RoundSnapshot(
    val tasks: List<Task>,
    val checklist: List<ChecklistItem>,
)

/**
 * The one place tasks are written from. Every use case that changes a task
 * goes through here, so the updated time and the change history are never
 * forgotten by an individual feature.
 */
class TaskEditor @Inject constructor(
    private val tasks: TaskRepository,
    private val checklists: ChecklistRepository,
    private val history: ChangeHistoryRepository,
    private val time: TimeSource,
) {
    fun now(): Long = time.nowMillis()

    /**
     * Saves each (before, after) pair and records which fields changed.
     * Pairs where nothing changed are skipped, so an edit that ends up equal
     * to what was stored leaves no trace.
     */
    suspend fun apply(changes: List<Pair<Task, Task>>, now: Long = now()) {
        val real = changes.filter { (before, after) -> before.asHistoryFields() != after.asHistoryFields() }
        if (real.isEmpty()) return

        tasks.save(real.map { (_, after) -> after.copy(stamps = after.stamps.touched(now)) })
        history.record(
            real.flatMap { (before, after) ->
                diffFields(TASK_TABLE, after.id.value, before.asHistoryFields(), after.asHistoryFields(), now)
            },
        )
    }

    /**
     * Moves a repeating task on to [nextDue] and clears the progress of the
     * round just ended: sub-tasks are reopened and every checklist beneath
     * it is unticked, so the routine starts fresh.
     *
     * @param subtree the task and everything beneath it that is not in the trash
     */
    suspend fun startNewRound(task: Task, subtree: List<Task>, nextDue: LocalDate, now: Long): RoundSnapshot {
        val closedSteps = subtree.filter { it.id != task.id && (it.isDone || it.isMissed) }
        val ticked = checklists.getFor(subtree.map { it.id }).filter { it.checked && !it.stamps.isDeleted }

        apply(
            changes = listOf(task to task.copy(dueDate = nextDue, carryCount = 0)) +
                closedSteps.map { it to it.copy(completedAt = null, missedAt = null) },
            now = now,
        )
        if (ticked.isNotEmpty()) {
            checklists.save(ticked.map { it.copy(checked = false, stamps = it.stamps.touched(now)) })
        }
        return RoundSnapshot(tasks = listOf(task) + closedSteps, checklist = ticked)
    }
}
