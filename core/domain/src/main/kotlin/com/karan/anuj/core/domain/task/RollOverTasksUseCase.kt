package com.karan.anuj.core.domain.task

import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** What one roll-over did, mostly for tests and logging. */
data class RollOverResult(
    val carried: Int = 0,
    val missed: Int = 0,
    val waitingForDecision: Int = 0,
)

/**
 * Deals with every open task whose day has passed, using each task's
 * carry-over rule. Running it again on the same day changes nothing, so it
 * is safe to call every time the app comes to the front.
 */
class RollOverTasksUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val preferences: TaskPreferencesRepository,
    private val editor: TaskEditor,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(today: LocalDate): RollOverResult {
        val open = tasks.getOpen()
        val overdue = open.filter { task -> task.dueDate?.let { it < today } == true }
        if (overdue.isEmpty()) return RollOverResult()

        val default = preferences.preferences.first().defaultCarryOver
        val byId = open.associateBy { it.id }
        val now = editor.now()

        var carried = 0
        var missed = 0
        var waiting = 0
        val moves = mutableListOf<Pair<Task, Task>>()

        for (task in overdue) {
            val due = task.dueDate ?: continue
            val rule = CarryOverCalculator.effectiveRule(task, byId, default)
            val outcome = rule?.let { CarryOverCalculator.resolve(it, due, today, task.daysOff) } ?: CarryOutcome.Missed

            when (outcome) {
                is CarryOutcome.Moved -> {
                    moves += task to task.copy(dueDate = outcome.to, carryCount = task.carryCount + outcome.carries)
                    carried++
                }
                CarryOutcome.NeedsDecision -> waiting++
                CarryOutcome.Missed -> {
                    val repetition = task.repetition
                    if (repetition == null) {
                        moves += task to task.copy(missedAt = now)
                    } else {
                        skipMissedRounds(task, repetition, due, today, now)
                    }
                    missed++
                }
            }
        }
        editor.apply(moves, now)
        return RollOverResult(carried, missed, waiting)
    }

    /**
     * Records every round of a repeating task that went by undone and moves
     * the task to its first round that is today or later.
     */
    private suspend fun skipMissedRounds(task: Task, rule: Repetition, due: LocalDate, today: LocalDate, now: Long) {
        var date = due
        var recorded = 0
        while (date < today && recorded < MAX_RECORDED_ROUNDS) {
            tasks.addOccurrence(TaskOccurrence(ids.newId(), task.id, date, OccurrenceOutcome.MISSED, now))
            date = RepetitionCalculator.next(rule, date, task.daysOff)
            recorded++
        }
        /** For a task ignored for longer than a year, the remaining rounds are skipped without a row each. */
        if (date < today) date = RepetitionCalculator.nextAfter(rule, date, today.minusDays(1), task.daysOff)

        val subtree = tasks.getSubtree(task.id).filterNot { it.stamps.isDeleted }
        editor.startNewRound(task, subtree, date, now)
    }

    private companion object {
        const val MAX_RECORDED_ROUNDS = 366
    }
}
