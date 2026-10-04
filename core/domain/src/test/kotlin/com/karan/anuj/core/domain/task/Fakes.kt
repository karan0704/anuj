package com.karan.anuj.core.domain.task

import com.karan.anuj.core.domain.history.ChangeHistoryRepository
import com.karan.anuj.core.domain.history.RecordChange
import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.time.TimeSource
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory stand-ins for storage, so the task rules are tested without a database. */

class FakeClock(var now: Long = 1_000L) : TimeSource {
    override fun nowMillis(): Long = now
}

class FakeIds : IdGenerator {
    private var next = 0
    override fun newId(): String = "id${++next}"
}

class FakeHistory : ChangeHistoryRepository {
    val recorded = mutableListOf<RecordChange>()

    override suspend fun record(changes: List<RecordChange>) {
        recorded += changes
    }

    override fun observeFor(table: String, rowId: String): Flow<List<RecordChange>> = emptyFlow()
}

class FakeTaskRepository : TaskRepository {
    private val rows = MutableStateFlow<Map<TaskId, Task>>(emptyMap())
    private val rounds = MutableStateFlow<List<TaskOccurrence>>(emptyList())

    val all: Collection<Task> get() = rows.value.values
    val occurrences: List<TaskOccurrence> get() = rounds.value
    fun task(id: String): Task = rows.value.getValue(TaskId(id))

    private fun live(): Flow<List<Task>> = rows.map { it.values.filterNot { task -> task.stamps.isDeleted } }

    override fun observeOpen() = live().map { tasks -> tasks.filter { it.isOpen } }
    override fun observeAll() = live()
    override fun observeCompletedBetween(fromMillis: Long, toMillis: Long) =
        live().map { tasks -> tasks.filter { task -> task.completedAt?.let { it in fromMillis until toMillis } == true } }

    override fun observeChildProgress() = live().map { tasks ->
        tasks.filter { it.parentId != null }
            .groupBy { it.parentId!! }
            .mapValues { (_, children) -> ChildProgress(children.size, children.count { it.isDone }) }
    }

    override fun observeTask(id: TaskId) = rows.map { it[id] }
    override fun observeChildren(parentId: TaskId) = live().map { tasks -> tasks.filter { it.parentId == parentId } }
    override fun observeTrash() = rows.map { it.values.filter { task -> task.stamps.isDeleted } }

    override suspend fun get(id: TaskId): Task? = rows.value[id]
    override suspend fun getOpen(): List<Task> = all.filter { it.isOpen }
    override suspend fun getTrash(): List<Task> = all.filter { it.stamps.isDeleted }

    override suspend fun getSubtree(id: TaskId): List<Task> {
        val root = rows.value[id] ?: return emptyList()
        val result = mutableListOf(root)
        var frontier = listOf(id)
        while (frontier.isNotEmpty()) {
            val children = all.filter { it.parentId in frontier }
            result += children
            frontier = children.map { it.id }
        }
        return result
    }

    override suspend fun save(tasks: List<Task>) {
        /** Mirrors the real storage rule: a child cannot be saved before its parent exists. */
        val known = rows.value.keys.toMutableSet()
        tasks.forEach { task ->
            check(task.parentId == null || task.parentId in known) { "Parent of ${task.name} saved after it" }
            known += task.id
        }
        rows.update { it + tasks.associateBy { task -> task.id } }
    }

    override suspend fun purge(ids: List<TaskId>) {
        rows.update { it - ids.toSet() }
        rounds.update { list -> list.filterNot { it.taskId in ids } }
    }

    override suspend fun search(query: SearchQuery): List<Task> = all.filter { task ->
        !task.stamps.isDeleted && query.words.all { word ->
            (task.name + " " + task.description).lowercase().split(" ").any { it.startsWith(word) }
        }
    }

    override suspend fun recentNames(limit: Int): List<String> =
        all.sortedByDescending { it.stamps.createdAt }.map { it.name }.distinct().take(limit)

    override fun observeOccurrencesBetween(fromMillis: Long, toMillis: Long) =
        rounds.map { list -> list.filter { it.at in fromMillis until toMillis } }

    override suspend fun addOccurrence(occurrence: TaskOccurrence) = rounds.update { it + occurrence }
    override suspend fun removeOccurrence(id: String) = rounds.update { list -> list.filterNot { it.id == id } }
}

class FakeAttachmentRepository : AttachmentRepository {
    val rows = mutableListOf<Attachment>()
    override fun observeFor(taskId: TaskId): Flow<List<Attachment>> = emptyFlow()
    override suspend fun getFor(taskIds: List<TaskId>): List<Attachment> = rows.filter { it.taskId in taskIds }
    override suspend fun save(attachment: Attachment) {
        rows.removeAll { it.id == attachment.id }
        rows += attachment
    }
}

class FakeFileStore : AttachmentFileStore {
    val deleted = mutableListOf<String>()
    override suspend fun importFrom(sourceUri: String): String = "imported.jpg"
    override suspend fun reserveForCamera(): String = "camera.jpg"
    override fun shareableUriFor(fileName: String): String = "content://$fileName"
    override fun pathOf(fileName: String): String = "/files/$fileName"
    override suspend fun delete(fileNames: List<String>) {
        deleted += fileNames
    }
}

class FakeTaskPreferences(initial: TaskPreferences = TaskPreferences()) : TaskPreferencesRepository {
    private val state = MutableStateFlow(initial)
    override val preferences: Flow<TaskPreferences> = state
    override suspend fun setDefaultCarryOver(rule: CarryOverRule) = state.update { it.copy(defaultCarryOver = rule) }
    override suspend fun setCarryLimit(limit: Int) = state.update { it.copy(carryLimit = limit) }
    override suspend fun setDayParts(parts: DayParts) = state.update { it.copy(dayParts = parts) }
    override suspend fun setEstimateChoices(minutes: List<Int>) = state.update { it.copy(estimateChoices = minutes) }
}

/** Everything a task use-case test needs, wired together. */
class TaskWorld {
    val clock = FakeClock()
    val ids = FakeIds()
    val history = FakeHistory()
    val tasks = FakeTaskRepository()
    val attachments = FakeAttachmentRepository()
    val files = FakeFileStore()
    val preferences = FakeTaskPreferences()
    val editor = TaskEditor(tasks, history, clock)

    /** Stores a task directly, bypassing the use cases, to set a scene. */
    suspend fun given(
        id: String,
        parent: String? = null,
        due: LocalDate? = null,
        repetition: Repetition? = null,
        carryOver: CarryOverRule? = null,
        carryCount: Int = 0,
        completedAt: Long? = null,
        deletedAt: Long? = null,
        name: String = id,
    ): Task {
        val task = Task(
            id = TaskId(id),
            parentId = parent?.let(::TaskId),
            name = name,
            dueDate = due,
            repetition = repetition,
            carryOver = carryOver,
            carryCount = carryCount,
            completedAt = completedAt,
            stamps = RecordStamps(createdAt = clock.now, updatedAt = clock.now, deletedAt = deletedAt),
        )
        tasks.save(listOf(task))
        return task
    }
}

/** Monday 5 October 2026; the tests count weekdays from here. */
val MONDAY: LocalDate = LocalDate.of(2026, 10, 5)
