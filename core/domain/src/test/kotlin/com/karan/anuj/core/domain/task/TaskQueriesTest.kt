package com.karan.anuj.core.domain.task

import com.karan.anuj.core.domain.record.RecordStamps
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskQueriesTest {

    private val world = TaskWorld()
    private val observeToday = ObserveTodayUseCase(world.tasks, world.preferences)
    private val observeInbox = ObserveInboxUseCase(world.tasks)
    private val observeTree = ObserveTaskTreeUseCase(world.tasks)
    private val applyTemplates = ApplyTemplatesUseCase(world.tasks, world.checklists, world.ids, world.clock)
    private val complete = CompleteTaskUseCase(world.tasks, world.editor, world.ids)
    private val checklist = ChecklistActions(world.checklists, world.ids, world.clock)
    private val tags = TagActions(FakeTagRepository(), world.ids, world.clock)

    /** The test day runs from 0 to 100,000 on the fake clock. */
    private suspend fun today() = observeToday(MONDAY, dayStartMillis = 0, dayEndMillis = 100_000).first()

    @Test
    fun `today separates overdue tasks from today's and leaves the rest out`() = runTest {
        world.given("overdue", due = MONDAY.minusDays(2), carryOver = CarryOverRule.AskMe)
        world.given("today", due = MONDAY)
        world.given("tomorrow", due = MONDAY.plusDays(1))
        world.given("undated")

        val view = today()

        assertEquals(listOf("overdue"), view.needsDecision.map { it.task.name })
        assertEquals(listOf("today"), view.due.map { it.task.name })
    }

    @Test
    fun `today lists timed tasks in clock order before untimed ones`() = runTest {
        world.given("untimed", due = MONDAY)
        world.tasks.save(listOf(world.given("evening", due = MONDAY).copy(dueTime = LocalTime.of(18, 0))))
        world.tasks.save(listOf(world.given("morning", due = MONDAY).copy(dueTime = LocalTime.of(7, 0))))

        assertEquals(listOf("morning", "evening", "untimed"), today().due.map { it.task.name })
    }

    @Test
    fun `a task carried as often as the limit is flagged as stuck`() = runTest {
        world.preferences.setCarryLimit(3)
        world.given("stuck", due = MONDAY, carryCount = 3)
        world.given("fine", due = MONDAY, carryCount = 2)

        val byName = today().due.associate { it.task.name to it.stuck }

        assertEquals(mapOf("stuck" to true, "fine" to false), byName)
    }

    @Test
    fun `today shows sub-task progress on a task that has sub-tasks`() = runTest {
        world.given("parent", due = MONDAY)
        world.given("a", parent = "parent", completedAt = 5)
        world.given("b", parent = "parent")
        world.given("plain", due = MONDAY)

        val items = today().due.associateBy { it.task.name }

        assertEquals(ChildProgress(total = 2, done = 1), items.getValue("parent").progress)
        assertNull(items.getValue("plain").progress)
    }

    @Test
    fun `done today holds finished tasks and finished rounds, newest first, and nothing from other days`() = runTest {
        world.given("yesterday", completedAt = -5)
        world.given("one-off", due = MONDAY)
        world.given("routine", due = MONDAY, repetition = Repetition.Daily())

        world.clock.now = 2_000
        complete(TaskId("one-off"), MONDAY)
        world.clock.now = 3_000
        complete(TaskId("routine"), MONDAY)

        val done = today().done

        assertEquals(listOf("routine", "one-off"), done.map { it.name })
        assertNull("a finished round cannot be reopened from the list", done[0].taskId)
        assertEquals(TaskId("one-off"), done[1].taskId)
    }

    @Test
    fun `the inbox holds undated top-level thoughts, newest first`() = runTest {
        world.given("older")
        world.clock.now = 2_000
        world.given("newer")
        world.given("dated", due = MONDAY)
        world.given("child", parent = "older")
        world.given("done", completedAt = 5)

        assertEquals(listOf("newer", "older"), observeInbox().first().map { it.name })
    }

    @Test
    fun `the tree leaves finished tasks out unless asked for them`() = runTest {
        world.given("open")
        world.given("done", completedAt = 5)
        world.given("trashed", deletedAt = 5)

        assertEquals(listOf("open"), observeTree(includeFinished = false).first().tasks.map { it.name })
        assertEquals(setOf("open", "done"), observeTree(includeFinished = true).first().tasks.map { it.name }.toSet())
    }

    @Test
    fun `templates become repeating tasks with their steps and checklist in order`() = runTest {
        applyTemplates(BuiltInTemplates.all, today = MONDAY)

        val morning = world.tasks.all.single { it.name == "Morning routine" }
        val steps = world.tasks.all.filter { it.parentId == morning.id }.sortedWith(TaskTree.treeOrder)
        val leaving = world.tasks.all.single { it.name == "Leaving home" }

        assertEquals(MONDAY, morning.dueDate)
        assertEquals(LocalTime.of(7, 0), morning.dueTime)
        assertEquals(Repetition.Daily(), morning.repetition)
        assertEquals("Drink a glass of water", steps.first().name)
        assertEquals("Eat breakfast", steps.last().name)
        assertEquals(
            listOf("Keys in pocket", "Phone and wallet", "Lights and fans off", "Taps closed", "Door locked"),
            world.checklists.observeFor(leaving.id).first().sortedBy { it.position }.map { it.text },
        )
        assertEquals(BuiltInTemplates.all.size, world.tasks.all.count { it.parentId == null })
    }

    @Test
    fun `checklist lines are added at the end and blank ones ignored`() = runTest {
        world.given("t")

        checklist.add(TaskId("t"), "first")
        checklist.add(TaskId("t"), "   ")
        checklist.add(TaskId("t"), " second ")

        val items = world.checklists.observeFor(TaskId("t")).first().sortedBy { it.position }
        assertEquals(listOf("first" to 0, "second" to 1), items.map { it.text to it.position })
    }

    @Test
    fun `a removed checklist line disappears and can be brought back`() = runTest {
        world.given("t")
        checklist.add(TaskId("t"), "line")
        val item = world.checklists.all.single()

        checklist.remove(item)
        assertTrue(world.checklists.observeFor(TaskId("t")).first().isEmpty())

        checklist.restore(world.checklists.all.single())
        assertFalse(world.checklists.observeFor(TaskId("t")).first().isEmpty())
    }

    @Test
    fun `creating a tag that already exists returns the existing one`() = runTest {
        val first = tags.create("Home", colorIndex = 0)
        val again = tags.create(" home ", colorIndex = 3)

        assertEquals(first, again)
        assertNull(tags.create("  ", colorIndex = 0))
    }

    @Test
    fun `starter tags are only added to an install that has no tags`() = runTest {
        tags.ensureStarterTags(listOf("Home", "Work"))
        tags.ensureStarterTags(listOf("Other"))

        assertEquals(listOf("Home", "Work"), tags.observe().first().map { it.name })
    }

    private class FakeTagRepository : TagRepository {
        private val rows = kotlinx.coroutines.flow.MutableStateFlow<List<Tag>>(emptyList())
        override fun observeAll() = rows
        override suspend fun getAll(): List<Tag> = rows.value
        override suspend fun save(tags: List<Tag>) {
            rows.value = rows.value.filterNot { old -> tags.any { it.id == old.id } } + tags
        }
    }

    @Test
    fun `record stamps on a fresh template task are set once`() = runTest {
        applyTemplates(listOf(BuiltInTemplates.all.first()), today = MONDAY)

        val parent = world.tasks.all.single { it.parentId == null }
        assertEquals(RecordStamps.created(world.clock.now), parent.stamps)
    }
}
