package com.karan.anuj.core.domain.task

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskLifecycleTest {

    private val world = TaskWorld()
    private val create = CreateTaskUseCase(world.tasks, world.ids, world.clock)
    private val update = UpdateTaskUseCase(world.tasks, world.editor)
    private val complete = CompleteTaskUseCase(world.tasks, world.editor, world.ids)
    private val undo = UndoCompletionUseCase(world.tasks, world.editor)
    private val reopen = ReopenTaskUseCase(world.tasks, world.editor)
    private val move = MoveTaskToDayUseCase(world.tasks, world.editor)
    private val duplicate = DuplicateTaskUseCase(world.tasks, world.ids, world.clock)

    @Test
    fun `a task needs nothing but a name`() = runTest {
        val task = create(TaskDraft(name = "  Buy milk  "), today = MONDAY)

        assertEquals("Buy milk", task?.name)
        assertNull(task?.dueDate)
        assertTrue(world.tasks.task(task!!.id.value).isOpen)
    }

    @Test
    fun `a blank name creates nothing`() = runTest {
        assertNull(create(TaskDraft(name = "   "), today = MONDAY))
        assertTrue(world.tasks.all.isEmpty())
    }

    @Test
    fun `a repeating task with no date starts today`() = runTest {
        val task = create(TaskDraft(name = "Water", repetition = Repetition.Daily()), today = MONDAY)

        assertEquals(MONDAY, task?.dueDate)
    }

    @Test
    fun `editing a task records what each changed field was before`() = runTest {
        world.given("t", name = "Buy milk")
        world.clock.now = 5_000

        update(TaskId("t")) { it.copy(name = "Buy oat milk", priority = Priority.HIGH) }

        assertEquals("Buy oat milk", world.tasks.task("t").name)
        assertEquals(5_000, world.tasks.task("t").stamps.updatedAt)
        assertEquals(
            listOf("name" to "Buy milk", "priority" to "NONE"),
            world.history.recorded.map { it.field to it.oldValue },
        )
    }

    @Test
    fun `an edit that changes nothing leaves no history and no new updated time`() = runTest {
        world.given("t")
        world.clock.now = 5_000

        update(TaskId("t")) { it.copy(name = it.name) }

        assertTrue(world.history.recorded.isEmpty())
        assertEquals(1_000, world.tasks.task("t").stamps.updatedAt)
    }

    @Test
    fun `a name cannot be edited to blank`() = runTest {
        world.given("t", name = "Buy milk")

        update(TaskId("t")) { it.copy(name = "  ") }

        assertEquals("Buy milk", world.tasks.task("t").name)
    }

    @Test
    fun `a repeating task cannot lose its date through an edit`() = runTest {
        world.given("t", due = MONDAY, repetition = Repetition.Daily())

        update(TaskId("t")) { it.copy(dueDate = null) }

        assertEquals(MONDAY, world.tasks.task("t").dueDate)
    }

    @Test
    fun `completing a task also completes the open sub-tasks beneath it`() = runTest {
        world.given("parent")
        world.given("child", parent = "parent")
        world.given("grandchild", parent = "child")
        world.given("done before", parent = "parent", completedAt = 10)
        world.given("other")
        world.clock.now = 2_000

        complete(TaskId("parent"), today = MONDAY)

        assertEquals(2_000L, world.tasks.task("parent").completedAt)
        assertEquals(2_000L, world.tasks.task("child").completedAt)
        assertEquals(2_000L, world.tasks.task("grandchild").completedAt)
        assertEquals(10L, world.tasks.task("done before").completedAt)
        assertTrue(world.tasks.task("other").isOpen)
    }

    @Test
    fun `undo reopens exactly what the completion closed`() = runTest {
        world.given("parent")
        world.given("child", parent = "parent")
        world.given("done before", parent = "parent", completedAt = 10)

        val token = complete(TaskId("parent"), today = MONDAY)
        undo(token!!)

        assertTrue(world.tasks.task("parent").isOpen)
        assertTrue(world.tasks.task("child").isOpen)
        assertEquals(10L, world.tasks.task("done before").completedAt)
    }

    @Test
    fun `completing an already finished task does nothing`() = runTest {
        world.given("t", completedAt = 10)

        assertNull(complete(TaskId("t"), today = MONDAY))
        assertEquals(10L, world.tasks.task("t").completedAt)
    }

    @Test
    fun `completing a repeating task moves it to its next day instead of closing it`() = runTest {
        world.given("routine", due = MONDAY, repetition = Repetition.Daily(), carryCount = 2)

        complete(TaskId("routine"), today = MONDAY)

        val routine = world.tasks.task("routine")
        assertTrue(routine.isOpen)
        assertEquals(MONDAY.plusDays(1), routine.dueDate)
        assertEquals(0, routine.carryCount)
        assertEquals(listOf(MONDAY to OccurrenceOutcome.DONE), world.tasks.occurrences.map { it.date to it.outcome })
    }

    @Test
    fun `a new round starts with every step beneath it reopened`() = runTest {
        world.given("routine", due = MONDAY, repetition = Repetition.Daily())
        world.given("step", parent = "routine", completedAt = 10)
        world.given("step of step", parent = "step", completedAt = 10)

        complete(TaskId("routine"), today = MONDAY)

        assertTrue(world.tasks.task("step").isOpen)
        assertTrue(world.tasks.task("step of step").isOpen)
    }

    @Test
    fun `undoing a repeating completion restores the day and the steps and removes the round`() = runTest {
        world.given("routine", due = MONDAY, repetition = Repetition.Daily(), carryCount = 2)
        world.given("step", parent = "routine", completedAt = 10)

        val token = complete(TaskId("routine"), today = MONDAY)
        undo(token!!)

        assertEquals(MONDAY, world.tasks.task("routine").dueDate)
        assertEquals(2, world.tasks.task("routine").carryCount)
        assertEquals(10L, world.tasks.task("step").completedAt)
        assertTrue(world.tasks.occurrences.isEmpty())
    }

    @Test
    fun `reopening clears done and missed`() = runTest {
        world.given("t", completedAt = 10)

        reopen(TaskId("t"))

        assertTrue(world.tasks.task("t").isOpen)
    }

    @Test
    fun `pushing a task back counts as a carry, a plain reschedule does not`() = runTest {
        world.given("pushed", due = MONDAY)
        world.given("rescheduled", due = MONDAY)

        move(TaskId("pushed"), MONDAY.plusDays(1), countsAsCarry = true)
        move(TaskId("rescheduled"), MONDAY.plusDays(1), countsAsCarry = false)

        assertEquals(1, world.tasks.task("pushed").carryCount)
        assertEquals(0, world.tasks.task("rescheduled").carryCount)
        assertEquals(MONDAY.plusDays(1), world.tasks.task("pushed").dueDate)
    }

    @Test
    fun `a repeating task keeps its date when asked to drop it`() = runTest {
        world.given("routine", due = MONDAY, repetition = Repetition.Daily())

        move(TaskId("routine"), date = null, countsAsCarry = false)

        assertEquals(MONDAY, world.tasks.task("routine").dueDate)
    }

    @Test
    fun `a copy has its own steps and starts fresh`() = runTest {
        world.given("parent", due = MONDAY, carryCount = 3, name = "Pack bag")
        world.given("child", parent = "parent", completedAt = 10, name = "Charger")

        val copyId = duplicate(TaskId("parent")) { "$it (copy)" }

        assertNotNull(copyId)
        val copy = world.tasks.task(copyId!!.value)
        val copiedChild = world.tasks.all.single { it.parentId == copyId }
        assertEquals("Pack bag (copy)", copy.name)
        assertEquals(MONDAY, copy.dueDate)
        assertEquals(0, copy.carryCount)
        assertEquals("Charger", copiedChild.name)
        assertTrue(copiedChild.isOpen)
        assertEquals(10L, world.tasks.task("child").completedAt)
    }

    @Test
    fun `a copy of a sub-task stays under the same parent`() = runTest {
        world.given("parent")
        world.given("child", parent = "parent")

        val copyId = duplicate(TaskId("child")) { "$it (copy)" }

        assertEquals(TaskId("parent"), world.tasks.task(copyId!!.value).parentId)
    }
}
