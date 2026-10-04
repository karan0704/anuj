package com.karan.anuj.core.domain.task

import com.karan.anuj.core.domain.record.RecordStamps
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.SUNDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Codecs, search words and the tree layout: the small pure pieces. */
class TaskValuesTest {

    @Test
    fun `every repetition survives being stored and read back`() {
        val rules = listOf(
            Repetition.Daily(),
            Repetition.Daily(every = 3),
            Repetition.Weekly(every = 2, days = setOf(DayOfWeek.MONDAY, FRIDAY)),
            Repetition.Weekly(),
            Repetition.Monthly(every = 1, dayOfMonth = 31),
            Repetition.Yearly(every = 1, month = 2, dayOfMonth = 29),
        )

        rules.forEach { assertEquals(it, RepetitionCodec.decode(RepetitionCodec.encode(it))) }
    }

    @Test
    fun `every carry-over rule survives being stored and read back`() {
        val rules = listOf(
            CarryOverRule.NextDay,
            CarryOverRule.NextWeek,
            CarryOverRule.NextMonth,
            CarryOverRule.NextYear,
            CarryOverRule.InDays(4),
            CarryOverRule.OnDate(MONDAY),
            CarryOverRule.AskMe,
            CarryOverRule.DontCarry,
        )

        rules.forEach { assertEquals(it, CarryOverCodec.decode(CarryOverCodec.encode(it))) }
    }

    @Test
    fun `nothing stored reads back as not set`() {
        assertNull(RepetitionCodec.encode(null))
        assertNull(RepetitionCodec.decode(null))
        assertNull(CarryOverCodec.decode(null))
    }

    @Test
    fun `text this version does not understand reads back as not set instead of crashing`() {
        listOf("", "HOURLY;1", "DAILY", "DAILY;x", "MONTHLY;1", "YEARLY;1;2").forEach {
            assertNull("repetition '$it'", RepetitionCodec.decode(it))
        }
        listOf("", "SOMEDAY", "IN_DAYS", "IN_DAYS;x", "ON_DATE;x").forEach {
            assertNull("carry-over '$it'", CarryOverCodec.decode(it))
        }
    }

    @Test
    fun `weekdays survive being stored as a number`() {
        val sets = listOf(emptySet(), setOf(DayOfWeek.MONDAY), setOf(FRIDAY, SUNDAY), DayOfWeek.entries.toSet())

        sets.forEach { assertEquals(it, WeekdaysCodec.decode(WeekdaysCodec.encode(it))) }
        assertEquals(1, WeekdaysCodec.encode(setOf(DayOfWeek.MONDAY)))
        assertEquals(64, WeekdaysCodec.encode(setOf(SUNDAY)))
    }

    @Test
    fun `search keeps only words and matches each from its start`() {
        assertEquals("buy* milk*", SearchQuery.from("  Buy   MILK!! ")?.matchExpression)
    }

    @Test
    fun `search syntax typed by the user is stripped, not passed to the database`() {
        assertEquals("a* or* b* c*", SearchQuery.from("a OR b* \"c\" -")?.matchExpression)
    }

    @Test
    fun `search with nothing but symbols or spaces is no search`() {
        assertNull(SearchQuery.from("   "))
        assertNull(SearchQuery.from("*-\"()"))
    }

    private fun task(id: String, parent: String? = null, priority: Priority = Priority.NONE, createdAt: Long = 0) = Task(
        id = TaskId(id),
        parentId = parent?.let(::TaskId),
        name = id,
        priority = priority,
        stamps = RecordStamps.created(createdAt),
    )

    private fun rows(nodes: List<TaskNode>) = nodes.map { "${"  ".repeat(it.depth)}${it.task.name}" }

    @Test
    fun `only expanded tasks show their children`() {
        val tasks = listOf(
            task("a", createdAt = 1),
            task("a1", parent = "a", createdAt = 2),
            task("a1x", parent = "a1", createdAt = 3),
            task("b", createdAt = 4),
            task("b1", parent = "b", createdAt = 5),
        )

        val collapsed = TaskTree.flatten(tasks, expanded = emptySet())
        val oneOpen = TaskTree.flatten(tasks, expanded = setOf(TaskId("a")))
        val allOpen = TaskTree.flatten(tasks, expanded = setOf(TaskId("a"), TaskId("a1"), TaskId("b")))

        assertEquals(listOf("a", "b"), rows(collapsed))
        assertEquals(listOf("a", "  a1", "b"), rows(oneOpen))
        assertEquals(listOf("a", "  a1", "    a1x", "b", "  b1"), rows(allOpen))
    }

    @Test
    fun `rows report whether they can be expanded`() {
        val tasks = listOf(task("a"), task("a1", parent = "a"), task("b"))

        val nodes = TaskTree.flatten(tasks, expanded = setOf(TaskId("a")))

        assertEquals(listOf(true, false, false), nodes.map { it.hasChildren })
        assertEquals(listOf(true, false, false), nodes.map { it.expanded })
    }

    @Test
    fun `higher priority comes first, then older tasks`() {
        val tasks = listOf(
            task("old low", createdAt = 1),
            task("new low", createdAt = 2),
            task("high", priority = Priority.HIGH, createdAt = 3),
        )

        assertEquals(listOf("high", "old low", "new low"), rows(TaskTree.flatten(tasks, emptySet())))
    }

    @Test
    fun `a task whose parent is not in the list is shown at the top level`() {
        val tasks = listOf(task("orphan", parent = "hidden"), task("a"))

        assertEquals(setOf("orphan", "a"), rows(TaskTree.flatten(tasks, emptySet())).toSet())
    }

    @Test
    fun `tasks that are each other's parent are each drawn once`() {
        val tasks = listOf(task("a", parent = "b"), task("b", parent = "a"))

        val nodes = TaskTree.flatten(tasks, expanded = setOf(TaskId("a"), TaskId("b")))

        assertEquals(nodes.map { it.task.id }.distinct(), nodes.map { it.task.id })
    }

    @Test
    fun `ancestors are listed nearest first`() {
        val tasks = listOf(task("root"), task("mid", parent = "root"), task("leaf", parent = "mid"))
        val byId = tasks.associateBy { it.id }

        assertEquals(listOf("mid", "root"), TaskTree.ancestorsOf(tasks[2], byId).map { it.name })
    }
}
