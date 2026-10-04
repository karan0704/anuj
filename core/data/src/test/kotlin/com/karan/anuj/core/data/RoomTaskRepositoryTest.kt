package com.karan.anuj.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.karan.anuj.core.data.db.AnujDatabase
import com.karan.anuj.core.data.task.RoomAttachmentRepository
import com.karan.anuj.core.data.task.RoomNoteRepository
import com.karan.anuj.core.data.task.RoomTagRepository
import com.karan.anuj.core.data.task.RoomTaskRepository
import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.task.Attachment
import com.karan.anuj.core.domain.task.CarryOverRule
import com.karan.anuj.core.domain.task.ChildProgress
import com.karan.anuj.core.domain.task.Energy
import com.karan.anuj.core.domain.task.Note
import com.karan.anuj.core.domain.task.OccurrenceOutcome
import com.karan.anuj.core.domain.task.Priority
import com.karan.anuj.core.domain.task.Repetition
import com.karan.anuj.core.domain.task.SearchQuery
import com.karan.anuj.core.domain.task.Tag
import com.karan.anuj.core.domain.task.TagId
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TaskOccurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the real queries against a real SQLite on the computer. The database
 * here is not encrypted (the encryption library only exists on a phone);
 * the tables, indexes, search and foreign keys are identical.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomTaskRepositoryTest {

    private lateinit var db: AnujDatabase
    private lateinit var tasks: RoomTaskRepository
    private lateinit var notes: RoomNoteRepository
    private lateinit var tags: RoomTagRepository
    private lateinit var attachments: RoomAttachmentRepository

    private val day = LocalDate.of(2026, 10, 5)

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AnujDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val io = Dispatchers.Unconfined
        tasks = RoomTaskRepository({ db.taskDao() }, io)
        notes = RoomNoteRepository({ db.noteDao() }, io)
        tags = RoomTagRepository({ db.tagDao() }, io)
        attachments = RoomAttachmentRepository({ db.attachmentDao() }, io)
    }

    @After
    fun close() = db.close()

    private fun task(
        id: String,
        parent: String? = null,
        name: String = id,
        description: String = "",
        completedAt: Long? = null,
        missedAt: Long? = null,
        deletedAt: Long? = null,
        createdAt: Long = 1,
        updatedAt: Long = createdAt,
        tags: Set<String> = emptySet(),
    ) = Task(
        id = TaskId(id),
        parentId = parent?.let(::TaskId),
        name = name,
        description = description,
        completedAt = completedAt,
        missedAt = missedAt,
        tagIds = tags.map(::TagId).toSet(),
        stamps = RecordStamps(createdAt, updatedAt, deletedAt),
    )

    private suspend fun save(vararg rows: Task) = tasks.save(rows.toList())

    private suspend fun search(text: String) = tasks.search(SearchQuery.from(text)!!).map { it.name }

    private suspend fun givenTags(vararg ids: String) =
        tags.save(ids.mapIndexed { index, id -> Tag(TagId(id), id, index, RecordStamps.created(index.toLong())) })

    @Test
    fun `every field of a task survives being saved and read back`() = runTest {
        givenTags("home", "health")
        save(task("parent"))
        val full = Task(
            id = TaskId("t"),
            parentId = TaskId("parent"),
            name = "Take medication",
            description = "With food",
            dueDate = day,
            dueTime = LocalTime.of(18, 30),
            repetition = Repetition.Weekly(every = 2, days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            daysOff = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            priority = Priority.HIGH,
            energy = Energy.LOW,
            estimatedMinutes = 15,
            carryOver = CarryOverRule.InDays(3),
            carryCount = 2,
            tagIds = setOf(TagId("home"), TagId("health")),
            completedAt = 500,
            missedAt = 600,
            stamps = RecordStamps(createdAt = 100, updatedAt = 200, deletedAt = 300),
        )

        save(full)

        assertEquals(full, tasks.get(TaskId("t")))
    }

    @Test
    fun `a task with nothing but a name reads back with everything else unset`() = runTest {
        val bare = task("t")

        save(bare)

        assertEquals(bare, tasks.get(TaskId("t")))
        assertNull(tasks.get(TaskId("missing")))
    }

    @Test
    fun `open tasks leave out done, missed and trashed ones, and all tasks leave out only the trash`() = runTest {
        save(task("open"), task("done", completedAt = 5), task("missed", missedAt = 5), task("trashed", deletedAt = 5))

        assertEquals(setOf("open"), tasks.observeOpen().first().map { it.name }.toSet())
        assertEquals(setOf("open"), tasks.getOpen().map { it.name }.toSet())
        assertEquals(setOf("open", "done", "missed"), tasks.observeAll().first().map { it.name }.toSet())
        assertEquals(setOf("trashed"), tasks.observeTrash().first().map { it.name }.toSet())
        assertEquals(setOf("trashed"), tasks.getTrash().map { it.name }.toSet())
    }

    @Test
    fun `a subtree is the task and everything beneath it at any depth, trashed rows included`() = runTest {
        save(
            task("root"),
            task("child", parent = "root"),
            task("grandchild", parent = "child"),
            task("trashed child", parent = "root", deletedAt = 5),
            task("sibling root"),
            task("sibling child", parent = "sibling root"),
        )

        assertEquals(
            setOf("root", "child", "grandchild", "trashed child"),
            tasks.getSubtree(TaskId("root")).map { it.name }.toSet(),
        )
        assertEquals(setOf("grandchild"), tasks.getSubtree(TaskId("grandchild")).map { it.name }.toSet())
        assertTrue(tasks.getSubtree(TaskId("missing")).isEmpty())
    }

    @Test
    fun `a batch may list a child before its parent`() = runTest {
        save(task("child", parent = "parent"), task("parent"))

        assertEquals(TaskId("parent"), tasks.get(TaskId("child"))?.parentId)
    }

    @Test
    fun `children are listed without trashed ones`() = runTest {
        save(task("p"), task("a", parent = "p"), task("b", parent = "p", deletedAt = 5), task("other"))

        assertEquals(listOf("a"), tasks.observeChildren(TaskId("p")).first().map { it.name })
    }

    @Test
    fun `sub-task counts cover done and open children and ignore trashed ones`() = runTest {
        save(
            task("p"),
            task("a", parent = "p", completedAt = 5),
            task("b", parent = "p"),
            task("c", parent = "p", deletedAt = 5),
            task("lonely"),
        )

        assertEquals(mapOf(TaskId("p") to ChildProgress(total = 2, done = 1)), tasks.observeChildProgress().first())
    }

    @Test
    fun `completed between includes the start instant and excludes the end instant`() = runTest {
        save(
            task("before", completedAt = 99),
            task("at start", completedAt = 100),
            task("inside", completedAt = 150),
            task("at end", completedAt = 200),
        )

        assertEquals(
            setOf("at start", "inside"),
            tasks.observeCompletedBetween(100, 200).first().map { it.name }.toSet(),
        )
    }

    @Test
    fun `search matches the start of words in a task's name or description`() = runTest {
        save(task("a", name = "Buy oat milk"), task("b", name = "Call bank", description = "about the milkshake refund"))

        assertEquals(setOf("Buy oat milk", "Call bank"), search("milk").toSet())
        assertEquals(listOf("Buy oat milk"), search("bu oa"))
        assertTrue("a word must match from its start", search("ilk").isEmpty())
    }

    @Test
    fun `search finds a task through its notes, and a step by its own name`() = runTest {
        save(task("with note", name = "Doctor"), task("trip", name = "Trip"), task("neither", name = "Other"))
        save(task("step", parent = "trip", name = "Pack passport"))
        notes.save(Note("n", TaskId("with note"), "bring the prescription", RecordStamps.created(1)))

        assertEquals(listOf("Doctor"), search("prescription"))
        assertEquals(listOf("Pack passport"), search("passport"))
    }

    @Test
    fun `search leaves out trashed tasks and removed notes`() = runTest {
        save(task("trashed", name = "Milk run", deletedAt = 5), task("live", name = "Errands"))
        notes.save(Note("n", TaskId("live"), "milk", RecordStamps(1, 1, deletedAt = 5)))

        assertTrue(search("milk").isEmpty())
    }

    @Test
    fun `search follows a rename`() = runTest {
        save(task("t", name = "Buy milk"))
        save(task("t", name = "Buy bread"))

        assertTrue(search("milk").isEmpty())
        assertEquals(listOf("Buy bread"), search("bread"))
    }

    @Test
    fun `search lists open tasks before finished ones, most recently changed first`() = runTest {
        save(
            task("done", name = "milk done", completedAt = 5, updatedAt = 900),
            task("old", name = "milk old", updatedAt = 100),
            task("new", name = "milk new", updatedAt = 500),
        )

        assertEquals(listOf("milk new", "milk old", "milk done"), search("milk"))
    }

    @Test
    fun `symbols typed into search are harmless`() = runTest {
        save(task("t", name = "Buy milk"))

        assertEquals(listOf("Buy milk"), search("\"milk\"* -(buy)"))
        /** OR is read as the plain word "or", which this task does not contain; the point is that nothing throws. */
        assertTrue(search("milk OR buy").isEmpty())
    }

    @Test
    fun `purging a task removes everything beneath and attached to it and nothing else`() = runTest {
        givenTags("home")
        save(task("root", tags = setOf("home")), task("child", parent = "root"), task("keep", tags = setOf("home")))
        notes.save(Note("n", TaskId("root"), "note", RecordStamps.created(1)))
        attachments.save(Attachment("a", TaskId("root"), "photo.jpg", RecordStamps.created(1)))
        tasks.addOccurrence(TaskOccurrence("o", TaskId("root"), day, OccurrenceOutcome.DONE, 50))

        tasks.purge(listOf(TaskId("root")))

        assertEquals(listOf("keep"), tasks.observeAll().first().map { it.name })
        assertNull("the sub-task goes with its parent", tasks.get(TaskId("child")))
        assertTrue(notes.observeFor(TaskId("root")).first().isEmpty())
        assertTrue(attachments.getFor(listOf(TaskId("root"))).isEmpty())
        assertTrue(tasks.observeOccurrencesBetween(0, 100).first().isEmpty())
        assertEquals(setOf(TagId("home")), tasks.get(TaskId("keep"))?.tagIds)
        assertTrue("purged text is gone from search too", search("root").isEmpty())
    }

    @Test
    fun `saving a task replaces its tags with exactly the ones it now carries`() = runTest {
        givenTags("home", "work", "health")
        save(task("t", tags = setOf("home", "work")))

        save(task("t", tags = setOf("work", "health")))

        assertEquals(setOf(TagId("work"), TagId("health")), tasks.get(TaskId("t"))?.tagIds)
        assertEquals(setOf(TagId("work"), TagId("health")), tasks.observeTask(TaskId("t")).first()?.tagIds)
        assertEquals(setOf(TagId("work"), TagId("health")), tasks.observeOpen().first().single().tagIds)
    }

    @Test
    fun `a removed tag is no longer offered but tasks keep working`() = runTest {
        givenTags("home")
        save(task("t", tags = setOf("home")))

        tags.save(listOf(Tag(TagId("home"), "home", 0, RecordStamps(1, 2, deletedAt = 2))))

        assertTrue(tags.observeAll().first().isEmpty())
        assertEquals("t", tasks.get(TaskId("t"))?.name)
    }

    @Test
    fun `recent names are distinct, newest first, without trashed tasks`() = runTest {
        save(
            task("1", name = "Water", createdAt = 1),
            task("2", name = "Laundry", createdAt = 2),
            task("3", name = "Water", createdAt = 3),
            task("4", name = "Secret", createdAt = 4, deletedAt = 5),
        )

        assertEquals(listOf("Water", "Laundry"), tasks.recentNames(5))
        assertEquals(listOf("Water"), tasks.recentNames(1))
    }

    @Test
    fun `rounds are read by the time they were recorded and can be removed`() = runTest {
        save(task("routine"))
        tasks.addOccurrence(TaskOccurrence("early", TaskId("routine"), day, OccurrenceOutcome.MISSED, at = 50))
        tasks.addOccurrence(TaskOccurrence("inside", TaskId("routine"), day, OccurrenceOutcome.DONE, at = 150))

        val inside = tasks.observeOccurrencesBetween(100, 200).first()
        assertEquals(listOf(TaskOccurrence("inside", TaskId("routine"), day, OccurrenceOutcome.DONE, 150)), inside)

        tasks.removeOccurrence("inside")
        assertTrue(tasks.observeOccurrencesBetween(100, 200).first().isEmpty())
    }

    @Test
    fun `a large batch is saved and purged without hitting database limits`() = runTest {
        val many = (1..1_500).map { task("t$it") }

        tasks.save(many)
        assertEquals(1_500, tasks.getOpen().size)

        tasks.purge(many.map { it.id })
        assertTrue(tasks.getOpen().isEmpty())
    }
}
