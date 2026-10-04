package com.karan.anuj.core.domain.task

import com.karan.anuj.core.domain.record.RecordStamps
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashAndRollOverTest {

    private val world = TaskWorld()
    private val delete = DeleteTaskUseCase(world.tasks, world.editor)
    private val restore = RestoreTaskUseCase(world.tasks, world.editor)
    private val purge = PurgeTasksUseCase(world.tasks, world.attachments, world.files)
    private val emptyTrash = EmptyTrashUseCase(world.tasks, purge)
    private val observeTrash = ObserveTrashUseCase(world.tasks)
    private val rollOver = RollOverTasksUseCase(world.tasks, world.preferences, world.editor, world.ids)

    private fun isDeleted(id: String) = world.tasks.task(id).stamps.isDeleted

    @Test
    fun `deleting a task takes everything beneath it to the trash`() = runTest {
        world.given("parent")
        world.given("child", parent = "parent")
        world.given("grandchild", parent = "child")
        world.given("other")

        delete(TaskId("parent"))

        assertTrue(isDeleted("parent") && isDeleted("child") && isDeleted("grandchild"))
        assertFalse(isDeleted("other"))
    }

    @Test
    fun `the trash shows one entry for a task deleted with its sub-tasks`() = runTest {
        world.given("parent")
        world.given("child", parent = "parent")

        delete(TaskId("parent"))

        assertEquals(listOf("parent"), observeTrash().first().map { it.name })
    }

    @Test
    fun `a sub-task deleted earlier has its own trash entry and stays deleted when the parent is restored`() = runTest {
        world.given("parent")
        world.given("kept", parent = "parent")
        world.given("removed earlier", parent = "parent")

        world.clock.now = 2_000
        delete(TaskId("removed earlier"))
        world.clock.now = 3_000
        delete(TaskId("parent"))

        assertEquals(setOf("parent", "removed earlier"), observeTrash().first().map { it.name }.toSet())

        world.clock.now = 4_000
        restore(TaskId("parent"))

        assertFalse(isDeleted("parent"))
        assertFalse(isDeleted("kept"))
        assertTrue(isDeleted("removed earlier"))
    }

    @Test
    fun `restoring a sub-task whose parent is still in the trash brings it back at the top level`() = runTest {
        world.given("parent")
        world.given("child", parent = "parent")
        world.clock.now = 2_000
        delete(TaskId("child"))
        world.clock.now = 3_000
        delete(TaskId("parent"))

        restore(TaskId("child"))

        assertFalse(isDeleted("child"))
        assertNull(world.tasks.task("child").parentId)
        assertTrue(isDeleted("parent"))
    }

    @Test
    fun `restoring a sub-task whose parent is still there keeps it under the parent`() = runTest {
        world.given("parent")
        world.given("child", parent = "parent")
        delete(TaskId("child"))

        restore(TaskId("child"))

        assertEquals(TaskId("parent"), world.tasks.task("child").parentId)
    }

    @Test
    fun `emptying the trash removes the tasks and their photo files, including photos already removed`() = runTest {
        world.given("parent")
        world.given("child", parent = "parent")
        world.given("other")
        world.attachments.save(Attachment("p1", TaskId("child"), "one.jpg", RecordStamps.created(0)))
        world.attachments.save(Attachment("p2", TaskId("parent"), "two.jpg", RecordStamps(0, 0, deletedAt = 5)))
        world.attachments.save(Attachment("p3", TaskId("other"), "keep.jpg", RecordStamps.created(0)))
        delete(TaskId("parent"))

        emptyTrash()

        assertEquals(listOf("other"), world.tasks.all.map { it.name })
        assertEquals(setOf("one.jpg", "two.jpg"), world.files.deleted.toSet())
    }

    @Test
    fun `a task left undone is carried to today and counts the days it slipped`() = runTest {
        world.given("t", due = MONDAY)

        val result = rollOver(today = MONDAY.plusDays(2))

        assertEquals(MONDAY.plusDays(2), world.tasks.task("t").dueDate)
        assertEquals(2, world.tasks.task("t").carryCount)
        assertEquals(RollOverResult(carried = 1), result)
    }

    @Test
    fun `running the roll-over twice on the same day changes nothing the second time`() = runTest {
        world.given("t", due = MONDAY)
        rollOver(today = MONDAY.plusDays(1))
        val afterFirst = world.tasks.task("t")

        val second = rollOver(today = MONDAY.plusDays(1))

        assertEquals(afterFirst, world.tasks.task("t"))
        assertEquals(RollOverResult(), second)
    }

    @Test
    fun `tasks due today or later, finished tasks and undated tasks are left alone`() = runTest {
        world.given("today", due = MONDAY)
        world.given("future", due = MONDAY.plusDays(3))
        world.given("undated")
        world.given("done", due = MONDAY.minusDays(3), completedAt = 5)

        rollOver(today = MONDAY)

        assertEquals(MONDAY, world.tasks.task("today").dueDate)
        assertEquals(MONDAY.plusDays(3), world.tasks.task("future").dueDate)
        assertNull(world.tasks.task("undated").dueDate)
        assertEquals(MONDAY.minusDays(3), world.tasks.task("done").dueDate)
    }

    @Test
    fun `a sub-task follows its parent's rule, and the app default applies when there is none`() = runTest {
        world.preferences.setDefaultCarryOver(CarryOverRule.NextDay)
        world.given("parent", carryOver = CarryOverRule.NextWeek)
        world.given("child", parent = "parent", due = MONDAY)
        world.given("loose", due = MONDAY)

        rollOver(today = MONDAY.plusDays(1))

        assertEquals(MONDAY.plusWeeks(1), world.tasks.task("child").dueDate)
        assertEquals(MONDAY.plusDays(1), world.tasks.task("loose").dueDate)
    }

    @Test
    fun `do not carry closes the task as missed on its own day`() = runTest {
        world.given("t", due = MONDAY, carryOver = CarryOverRule.DontCarry)
        world.clock.now = 9_000

        val result = rollOver(today = MONDAY.plusDays(1))

        val task = world.tasks.task("t")
        assertEquals(9_000L, task.missedAt)
        assertEquals(MONDAY, task.dueDate)
        assertFalse(task.isOpen)
        assertEquals(RollOverResult(missed = 1), result)
    }

    @Test
    fun `ask me leaves the task overdue for the user to decide`() = runTest {
        world.given("t", due = MONDAY, carryOver = CarryOverRule.AskMe)

        val result = rollOver(today = MONDAY.plusDays(4))

        assertEquals(MONDAY, world.tasks.task("t").dueDate)
        assertEquals(0, world.tasks.task("t").carryCount)
        assertEquals(RollOverResult(waitingForDecision = 1), result)
    }

    @Test
    fun `missed rounds of a repeating task are recorded and do not pile up`() = runTest {
        world.given("water", due = MONDAY, repetition = Repetition.Daily())
        world.given("step", parent = "water", completedAt = 10)

        rollOver(today = MONDAY.plusDays(3))

        val water = world.tasks.task("water")
        assertEquals(MONDAY.plusDays(3), water.dueDate)
        assertEquals(0, water.carryCount)
        assertTrue(water.isOpen)
        assertEquals(
            listOf(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2)),
            world.tasks.occurrences.filter { it.outcome == OccurrenceOutcome.MISSED }.map { it.date },
        )
        assertTrue("a missed round still starts the next one fresh", world.tasks.task("step").isOpen)
    }

    @Test
    fun `a missed weekly round moves to its next weekday, not to today`() = runTest {
        world.given("bins", due = MONDAY, repetition = Repetition.Weekly())

        rollOver(today = MONDAY.plusDays(2))

        assertEquals(MONDAY.plusWeeks(1), world.tasks.task("bins").dueDate)
    }

    @Test
    fun `a repeating task with carry-over switched on is carried like any other task`() = runTest {
        world.given("meds", due = MONDAY, repetition = Repetition.Daily(), carryOver = CarryOverRule.NextDay)

        rollOver(today = MONDAY.plusDays(1))

        assertEquals(MONDAY.plusDays(1), world.tasks.task("meds").dueDate)
        assertEquals(1, world.tasks.task("meds").carryCount)
        assertTrue(world.tasks.occurrences.isEmpty())
    }

    @Test
    fun `a carried task skips its days off`() = runTest {
        val friday = MONDAY.plusDays(4)
        world.tasks.save(listOf(world.given("t", due = friday).copy(daysOff = setOf(SATURDAY, SUNDAY))))

        rollOver(today = friday.plusDays(1))

        assertEquals(MONDAY.plusWeeks(1), world.tasks.task("t").dueDate)
    }

    @Test
    fun `every carried task leaves a history entry for its date and its count`() = runTest {
        world.given("t", due = MONDAY)

        rollOver(today = MONDAY.plusDays(1))

        assertEquals(setOf("dueDate", "carryCount"), world.history.recorded.map { it.field }.toSet())
    }
}
