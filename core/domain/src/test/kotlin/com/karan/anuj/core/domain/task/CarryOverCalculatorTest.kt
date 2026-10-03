package com.karan.anuj.core.domain.task

import com.karan.anuj.core.domain.record.RecordStamps
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarryOverCalculatorTest {

    private fun task(id: String, parent: String? = null, rule: CarryOverRule? = null, repetition: Repetition? = null) =
        Task(
            id = TaskId(id),
            parentId = parent?.let(::TaskId),
            name = id,
            carryOver = rule,
            repetition = repetition,
            stamps = RecordStamps.created(0),
        )

    @Test
    fun `next day lands on today and counts one carry per day missed`() {
        val outcome = CarryOverCalculator.resolve(CarryOverRule.NextDay, due = MONDAY, today = MONDAY.plusDays(3))

        assertEquals(CarryOutcome.Moved(MONDAY.plusDays(3), carries = 3), outcome)
    }

    @Test
    fun `next week keeps the weekday`() {
        val outcome = CarryOverCalculator.resolve(CarryOverRule.NextWeek, due = MONDAY, today = MONDAY.plusDays(1))

        assertEquals(CarryOutcome.Moved(MONDAY.plusWeeks(1), carries = 1), outcome)
    }

    @Test
    fun `next week is applied again when a whole week went by`() {
        val outcome = CarryOverCalculator.resolve(CarryOverRule.NextWeek, due = MONDAY, today = MONDAY.plusDays(9))

        assertEquals(CarryOutcome.Moved(MONDAY.plusWeeks(2), carries = 2), outcome)
    }

    @Test
    fun `next month from the 31st uses the last day of a shorter month`() {
        val outcome = CarryOverCalculator.resolve(
            CarryOverRule.NextMonth,
            due = LocalDate.of(2026, 1, 31),
            today = LocalDate.of(2026, 2, 1),
        )

        assertEquals(CarryOutcome.Moved(LocalDate.of(2026, 2, 28), carries = 1), outcome)
    }

    @Test
    fun `next year from 29 February lands on the 28th`() {
        val outcome = CarryOverCalculator.resolve(
            CarryOverRule.NextYear,
            due = LocalDate.of(2028, 2, 29),
            today = LocalDate.of(2028, 3, 1),
        )

        assertEquals(CarryOutcome.Moved(LocalDate.of(2029, 2, 28), carries = 1), outcome)
    }

    @Test
    fun `in N days steps by that gap until it reaches today`() {
        val outcome = CarryOverCalculator.resolve(CarryOverRule.InDays(3), due = MONDAY, today = MONDAY.plusDays(4))

        assertEquals(CarryOutcome.Moved(MONDAY.plusDays(6), carries = 2), outcome)
    }

    @Test
    fun `a fixed date still ahead is used`() {
        val friday = MONDAY.plusDays(4)

        val outcome = CarryOverCalculator.resolve(CarryOverRule.OnDate(friday), due = MONDAY, today = MONDAY.plusDays(1))

        assertEquals(CarryOutcome.Moved(friday, carries = 1), outcome)
    }

    @Test
    fun `a fixed date that has passed leaves the choice to the user`() {
        val outcome = CarryOverCalculator.resolve(CarryOverRule.OnDate(MONDAY), due = MONDAY.minusDays(2), today = MONDAY.plusDays(1))

        assertEquals(CarryOutcome.NeedsDecision, outcome)
    }

    @Test
    fun `ask me and do not carry move nothing`() {
        assertEquals(CarryOutcome.NeedsDecision, CarryOverCalculator.resolve(CarryOverRule.AskMe, MONDAY, MONDAY.plusDays(1)))
        assertEquals(CarryOutcome.Missed, CarryOverCalculator.resolve(CarryOverRule.DontCarry, MONDAY, MONDAY.plusDays(1)))
    }

    @Test
    fun `a carried task skips days off`() {
        val friday = MONDAY.plusDays(4)
        val saturday = MONDAY.plusDays(5)

        val outcome = CarryOverCalculator.resolve(CarryOverRule.NextDay, due = friday, today = saturday, daysOff = setOf(SATURDAY, SUNDAY))

        assertEquals(CarryOutcome.Moved(MONDAY.plusWeeks(1), carries = 1), outcome)
    }

    @Test
    fun `a task uses its own rule first`() {
        val parent = task("parent", rule = CarryOverRule.NextWeek)
        val child = task("child", parent = "parent", rule = CarryOverRule.DontCarry)

        val rule = CarryOverCalculator.effectiveRule(child, listOf(parent, child).associateBy { it.id }, CarryOverRule.NextDay)

        assertEquals(CarryOverRule.DontCarry, rule)
    }

    @Test
    fun `a task without a rule takes the nearest ancestor's`() {
        val grandparent = task("grandparent", rule = CarryOverRule.NextMonth)
        val parent = task("parent", parent = "grandparent")
        val child = task("child", parent = "parent")
        val byId = listOf(grandparent, parent, child).associateBy { it.id }

        assertEquals(CarryOverRule.NextMonth, CarryOverCalculator.effectiveRule(child, byId, CarryOverRule.NextDay))
    }

    @Test
    fun `with no rule anywhere above it the app default applies`() {
        val parent = task("parent")
        val child = task("child", parent = "parent")
        val byId = listOf(parent, child).associateBy { it.id }

        assertEquals(CarryOverRule.AskMe, CarryOverCalculator.effectiveRule(child, byId, CarryOverRule.AskMe))
    }

    @Test
    fun `a repeating task does not inherit a rule`() {
        val parent = task("parent", rule = CarryOverRule.NextWeek)
        val child = task("child", parent = "parent", repetition = Repetition.Daily())
        val byId = listOf(parent, child).associateBy { it.id }

        assertNull(CarryOverCalculator.effectiveRule(child, byId, CarryOverRule.NextDay))
    }

    @Test
    fun `a repeating task with its own rule keeps it`() {
        val task = task("task", rule = CarryOverRule.NextDay, repetition = Repetition.Daily())

        assertEquals(CarryOverRule.NextDay, CarryOverCalculator.effectiveRule(task, mapOf(task.id to task), CarryOverRule.AskMe))
    }

    @Test
    fun `tasks that are each other's parent fall back to the default instead of looping`() {
        val a = task("a", parent = "b")
        val b = task("b", parent = "a")
        val byId = listOf(a, b).associateBy { it.id }

        assertEquals(CarryOverRule.NextDay, CarryOverCalculator.effectiveRule(a, byId, CarryOverRule.NextDay))
    }
}
