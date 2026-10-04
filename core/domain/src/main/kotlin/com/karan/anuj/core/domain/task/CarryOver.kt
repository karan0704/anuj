package com.karan.anuj.core.domain.task

import java.time.DayOfWeek
import java.time.LocalDate

/** What happens to a task that was not done by the end of its day. */
sealed interface CarryOverRule {
    data object NextDay : CarryOverRule
    data object NextWeek : CarryOverRule
    data object NextMonth : CarryOverRule
    data object NextYear : CarryOverRule
    data class InDays(val days: Int) : CarryOverRule
    data class OnDate(val date: LocalDate) : CarryOverRule

    /** Leave it overdue and let the user choose. */
    data object AskMe : CarryOverRule

    /** Leave it on its day and close it as missed. */
    data object DontCarry : CarryOverRule
}

sealed interface CarryOutcome {
    /** @property carries how many times the rule had to be applied to reach a day that has not passed */
    data class Moved(val to: LocalDate, val carries: Int) : CarryOutcome
    data object NeedsDecision : CarryOutcome
    data object Missed : CarryOutcome
}

object CarryOverCalculator {

    /**
     * Applies [rule] to a task whose [due] day is before [today].
     *
     * The rule is applied as many times as it takes to reach today or later:
     * a "next day" task left alone for three days ends up on today having
     * been carried three times, which is what the carry count is meant to show.
     */
    fun resolve(
        rule: CarryOverRule,
        due: LocalDate,
        today: LocalDate,
        daysOff: Set<DayOfWeek> = emptySet(),
    ): CarryOutcome = when (rule) {
        CarryOverRule.AskMe -> CarryOutcome.NeedsDecision
        CarryOverRule.DontCarry -> CarryOutcome.Missed
        /** A fixed date can only be used once; if that date has passed too, there is nothing left to apply. */
        is CarryOverRule.OnDate ->
            if (rule.date >= today) CarryOutcome.Moved(rule.date.skippingDaysOff(daysOff), 1)
            else CarryOutcome.NeedsDecision
        CarryOverRule.NextDay -> stepUntilCurrent(due, today, daysOff) { it.plusDays(1) }
        CarryOverRule.NextWeek -> stepUntilCurrent(due, today, daysOff) { it.plusWeeks(1) }
        CarryOverRule.NextMonth -> stepUntilCurrent(due, today, daysOff) { it.plusMonths(1) }
        CarryOverRule.NextYear -> stepUntilCurrent(due, today, daysOff) { it.plusYears(1) }
        is CarryOverRule.InDays -> {
            val gap = rule.days.coerceAtLeast(1).toLong()
            stepUntilCurrent(due, today, daysOff) { it.plusDays(gap) }
        }
    }

    private inline fun stepUntilCurrent(
        due: LocalDate,
        today: LocalDate,
        daysOff: Set<DayOfWeek>,
        step: (LocalDate) -> LocalDate,
    ): CarryOutcome {
        var date = due
        var carries = 0
        while (date < today && carries < MAX_CARRIES) {
            date = step(date)
            carries++
        }
        /** Only reached for a task untouched for many years. */
        if (date < today) date = today
        return CarryOutcome.Moved(date.skippingDaysOff(daysOff), carries)
    }

    /**
     * The rule a task follows: its own, else the nearest ancestor's, else
     * [default]. A repeating task does not inherit: unless it has a rule of
     * its own, a missed round is marked missed so rounds never pile up.
     *
     * @return null for a repeating task with no rule of its own
     */
    fun effectiveRule(task: Task, byId: Map<TaskId, Task>, default: CarryOverRule): CarryOverRule? {
        task.carryOver?.let { return it }
        if (task.repetition != null) return null

        val seen = mutableSetOf(task.id)
        var parent = task.parentId?.let(byId::get)
        while (parent != null && seen.add(parent.id)) {
            parent.carryOver?.let { return it }
            parent = parent.parentId?.let(byId::get)
        }
        return default
    }

    private const val MAX_CARRIES = 5_000
}
