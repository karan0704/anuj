package com.karan.anuj.core.domain.task

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * How often a task comes round again. [every] is the gap in the rule's own
 * unit: `Weekly(every = 2)` is every second week.
 */
sealed interface Repetition {
    val every: Int

    data class Daily(override val every: Int = 1) : Repetition

    /** @property days the weekdays it falls on; empty means "the weekday it is currently due on" */
    data class Weekly(override val every: Int = 1, val days: Set<DayOfWeek> = emptySet()) : Repetition

    /** @property dayOfMonth 1–31; months that are shorter use their last day */
    data class Monthly(override val every: Int = 1, val dayOfMonth: Int) : Repetition

    /** 29 February falls on the 28th in years that are not leap years. */
    data class Yearly(override val every: Int = 1, val month: Int, val dayOfMonth: Int) : Repetition
}

/** Moves a date forward to the first day that is not one of [daysOff]. */
fun LocalDate.skippingDaysOff(daysOff: Set<DayOfWeek>): LocalDate {
    /** With every weekday switched off there is no valid day to land on, so the setting is ignored. */
    if (daysOff.size >= DayOfWeek.entries.size) return this
    var date = this
    while (date.dayOfWeek in daysOff) date = date.plusDays(1)
    return date
}

/** Works out when a repeating task is next due. Weeks run Monday to Sunday. */
object RepetitionCalculator {

    /** A stored rule with a gap below one is treated as one, rather than looping on the same day. */
    private val Repetition.step: Long get() = every.coerceAtLeast(1).toLong()

    /** The first date on the rule's rhythm that is strictly after [from], moved past any day off. */
    fun next(rule: Repetition, from: LocalDate, daysOff: Set<DayOfWeek> = emptySet()): LocalDate {
        val onRhythm = when (rule) {
            is Repetition.Daily -> from.plusDays(rule.step)
            is Repetition.Weekly -> nextWeekly(rule, from)
            is Repetition.Monthly -> nextMonthly(rule, from)
            is Repetition.Yearly -> nextYearly(rule, from)
        }
        return onRhythm.skippingDaysOff(daysOff)
    }

    /**
     * The next due date once a round is finished or missed: the first date on
     * the rhythm that is after both the round's own date and today. A round
     * dealt with late therefore never schedules the next one in the past.
     */
    fun nextAfter(
        rule: Repetition,
        due: LocalDate,
        today: LocalDate,
        daysOff: Set<DayOfWeek> = emptySet(),
    ): LocalDate {
        var date = due
        repeat(MAX_STEPS) {
            date = next(rule, date, daysOff)
            if (date > today) return date
        }
        /** Only reached for a task untouched for many years; restart the rhythm from today instead of stepping further. */
        return next(rule, today, daysOff)
    }

    private fun nextWeekly(rule: Repetition.Weekly, from: LocalDate): LocalDate {
        val days = rule.days.ifEmpty { setOf(from.dayOfWeek) }
        val laterThisWeek = days.filter { it > from.dayOfWeek }.minOrNull()
        if (laterThisWeek != null) return from.with(TemporalAdjusters.next(laterThisWeek))

        val mondayOfTargetWeek = from
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(rule.step)
        return mondayOfTargetWeek.with(TemporalAdjusters.nextOrSame(days.min()))
    }

    private fun nextMonthly(rule: Repetition.Monthly, from: LocalDate): LocalDate {
        val thisMonth = YearMonth.from(from).atClampedDay(rule.dayOfMonth)
        if (thisMonth > from) return thisMonth
        return YearMonth.from(from).plusMonths(rule.step).atClampedDay(rule.dayOfMonth)
    }

    private fun nextYearly(rule: Repetition.Yearly, from: LocalDate): LocalDate {
        val month = rule.month.coerceIn(1, 12)
        val thisYear = YearMonth.of(from.year, month).atClampedDay(rule.dayOfMonth)
        if (thisYear > from) return thisYear
        return YearMonth.of(from.year + rule.step.toInt(), month).atClampedDay(rule.dayOfMonth)
    }

    /** The asked-for day of the month, or the month's last day when the month is shorter. */
    private fun YearMonth.atClampedDay(dayOfMonth: Int): LocalDate =
        atDay(dayOfMonth.coerceIn(1, lengthOfMonth()))

    private const val MAX_STEPS = 5_000
}
