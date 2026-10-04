package com.karan.anuj.core.domain.task

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RepetitionCalculatorTest {

    private val weekend = setOf(SATURDAY, SUNDAY)

    private fun date(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

    @Test
    fun `the calendar the tests rely on starts on a Monday`() {
        assertEquals(DayOfWeek.MONDAY, MONDAY.dayOfWeek)
    }

    @Test
    fun `daily moves one day on`() {
        assertEquals(MONDAY.plusDays(1), RepetitionCalculator.next(Repetition.Daily(), MONDAY))
    }

    @Test
    fun `every third day moves three days on`() {
        assertEquals(MONDAY.plusDays(3), RepetitionCalculator.next(Repetition.Daily(every = 3), MONDAY))
    }

    @Test
    fun `daily with the weekend off goes from Friday to Monday`() {
        val friday = MONDAY.plusDays(4)

        assertEquals(MONDAY.plusWeeks(1), RepetitionCalculator.next(Repetition.Daily(), friday, weekend))
    }

    @Test
    fun `switching every weekday off is ignored rather than looping forever`() {
        val allDays = DayOfWeek.entries.toSet()

        assertEquals(MONDAY.plusDays(1), RepetitionCalculator.next(Repetition.Daily(), MONDAY, allDays))
    }

    @Test
    fun `a gap of zero is treated as one so the task still moves forward`() {
        assertEquals(MONDAY.plusDays(1), RepetitionCalculator.next(Repetition.Daily(every = 0), MONDAY))
    }

    @Test
    fun `weekly on chosen days goes to the next chosen day in the same week`() {
        val rule = Repetition.Weekly(days = setOf(DayOfWeek.MONDAY, WEDNESDAY, FRIDAY))

        assertEquals(MONDAY.plusDays(2), RepetitionCalculator.next(rule, MONDAY))
        assertEquals(MONDAY.plusDays(4), RepetitionCalculator.next(rule, MONDAY.plusDays(2)))
    }

    @Test
    fun `weekly wraps from the last chosen day to the first one of the next week`() {
        val rule = Repetition.Weekly(days = setOf(DayOfWeek.MONDAY, WEDNESDAY, FRIDAY))

        assertEquals(MONDAY.plusWeeks(1), RepetitionCalculator.next(rule, MONDAY.plusDays(4)))
    }

    @Test
    fun `every second week skips a week when wrapping`() {
        val rule = Repetition.Weekly(every = 2, days = setOf(DayOfWeek.MONDAY, FRIDAY))

        assertEquals(MONDAY.plusDays(4), RepetitionCalculator.next(rule, MONDAY))
        assertEquals(MONDAY.plusWeeks(2), RepetitionCalculator.next(rule, MONDAY.plusDays(4)))
    }

    @Test
    fun `weekly with no days chosen keeps the weekday it is due on`() {
        val wednesday = MONDAY.plusDays(2)

        assertEquals(wednesday.plusWeeks(1), RepetitionCalculator.next(Repetition.Weekly(), wednesday))
    }

    @Test
    fun `monthly on the 31st uses the last day of shorter months and returns to the 31st`() {
        val rule = Repetition.Monthly(dayOfMonth = 31)

        val february = RepetitionCalculator.next(rule, date(2026, 1, 31))
        assertEquals(date(2026, 2, 28), february)
        assertEquals(date(2026, 3, 31), RepetitionCalculator.next(rule, february))
    }

    @Test
    fun `monthly on the 31st lands on 29 February in a leap year`() {
        assertEquals(date(2028, 2, 29), RepetitionCalculator.next(Repetition.Monthly(dayOfMonth = 31), date(2028, 1, 31)))
    }

    @Test
    fun `monthly uses this month when its day is still ahead`() {
        assertEquals(date(2026, 10, 15), RepetitionCalculator.next(Repetition.Monthly(dayOfMonth = 15), date(2026, 10, 10)))
    }

    @Test
    fun `monthly crosses the year end`() {
        assertEquals(date(2027, 1, 15), RepetitionCalculator.next(Repetition.Monthly(dayOfMonth = 15), date(2026, 12, 15)))
    }

    @Test
    fun `every third month moves three months on`() {
        assertEquals(date(2027, 1, 5), RepetitionCalculator.next(Repetition.Monthly(every = 3, dayOfMonth = 5), date(2026, 10, 5)))
    }

    @Test
    fun `yearly on 29 February falls on the 28th until the next leap year`() {
        val rule = Repetition.Yearly(month = 2, dayOfMonth = 29)

        assertEquals(date(2027, 2, 28), RepetitionCalculator.next(rule, date(2026, 2, 28)))
        assertEquals(date(2028, 2, 29), RepetitionCalculator.next(rule, date(2027, 2, 28)))
    }

    @Test
    fun `yearly uses this year when its date is still ahead`() {
        assertEquals(date(2026, 12, 25), RepetitionCalculator.next(Repetition.Yearly(month = 12, dayOfMonth = 25), date(2026, 10, 5)))
    }

    @Test
    fun `a monthly date that falls on a day off moves to the next working day`() {
        /** 1 November 2026 is a Sunday. */
        val next = RepetitionCalculator.next(Repetition.Monthly(dayOfMonth = 1), date(2026, 10, 1), weekend)

        assertEquals(date(2026, 11, 2), next)
    }

    @Test
    fun `finishing a round on time schedules the following one`() {
        assertEquals(MONDAY.plusDays(1), RepetitionCalculator.nextAfter(Repetition.Daily(), due = MONDAY, today = MONDAY))
    }

    @Test
    fun `finishing a round early still moves past its own date`() {
        val tuesday = MONDAY.plusDays(1)

        assertEquals(MONDAY.plusDays(2), RepetitionCalculator.nextAfter(Repetition.Daily(), due = tuesday, today = MONDAY))
    }

    @Test
    fun `finishing a round late never schedules the next one in the past`() {
        val thursday = MONDAY.plusDays(3)

        assertEquals(MONDAY.plusDays(4), RepetitionCalculator.nextAfter(Repetition.Daily(), due = MONDAY, today = thursday))
    }

    @Test
    fun `a late weekly round stays on its weekday`() {
        val rule = Repetition.Weekly(days = setOf(DayOfWeek.MONDAY))
        val threeWeeksLater = MONDAY.plusDays(17)

        assertEquals(MONDAY.plusWeeks(3), RepetitionCalculator.nextAfter(rule, due = MONDAY, today = threeWeeksLater))
    }

    @Test
    fun `a task untouched for decades restarts from today instead of hanging`() {
        val next = RepetitionCalculator.nextAfter(Repetition.Daily(), due = date(1990, 1, 1), today = MONDAY)

        assertEquals(MONDAY.plusDays(1), next)
    }
}
