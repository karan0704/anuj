package com.karan.anuj.core.domain.task

import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * A task as shown on the Today screen.
 *
 * @property progress sub-task counts, or null when it has no sub-tasks
 * @property stuck carried so many times that the app should ask what to do with it
 */
data class TodayItem(
    val task: Task,
    val progress: ChildProgress?,
    val stuck: Boolean,
)

/**
 * Something finished today.
 *
 * @property taskId set for a one-off task, which can be reopened from the
 * list; null for a finished round of a repeating task, which lives on as
 * its next round
 */
data class DoneEntry(
    val key: String,
    val taskId: TaskId?,
    val name: String,
    val at: Long,
)

data class TodayTasks(
    /** Overdue tasks whose rule leaves the choice to the user. */
    val needsDecision: List<TodayItem> = emptyList(),
    val due: List<TodayItem> = emptyList(),
    val done: List<DoneEntry> = emptyList(),
)

class ObserveTodayUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val preferences: TaskPreferencesRepository,
) {
    /**
     * @param dayStartMillis the instant [today] begins on this phone
     * @param dayEndMillis the instant the next day begins
     */
    operator fun invoke(today: LocalDate, dayStartMillis: Long, dayEndMillis: Long): Flow<TodayTasks> =
        combine(
            tasks.observeOpen(),
            tasks.observeCompletedBetween(dayStartMillis, dayEndMillis),
            tasks.observeOccurrencesBetween(dayStartMillis, dayEndMillis),
            tasks.observeChildProgress(),
            preferences.preferences,
        ) { open, completed, rounds, progress, prefs ->
            fun item(task: Task) = TodayItem(task, progress[task.id], stuck = task.carryCount >= prefs.carryLimit)

            val names = open.associate { it.id to it.name }
            val doneRounds = rounds
                .filter { it.outcome == OccurrenceOutcome.DONE }
                .mapNotNull { round -> names[round.taskId]?.let { DoneEntry(round.id, null, it, round.at) } }
            val doneTasks = completed.map { DoneEntry(it.id.value, it.id, it.name, it.completedAt ?: 0L) }

            TodayTasks(
                needsDecision = open
                    .filter { task -> task.dueDate?.let { it < today } == true }
                    .sortedWith(compareBy<Task> { it.dueDate }.then(TaskTree.dayOrder))
                    .map(::item),
                due = open.filter { it.dueDate == today }.sortedWith(TaskTree.dayOrder).map(::item),
                done = (doneTasks + doneRounds).sortedByDescending { it.at },
            )
        }
}

class ObserveInboxUseCase @Inject constructor(
    private val tasks: TaskRepository,
) {
    /** Thoughts captured but not sorted yet: top-level, no day, not repeating. Newest first. */
    operator fun invoke(): Flow<List<Task>> = tasks.observeOpen().map { open ->
        open
            .filter { it.parentId == null && it.dueDate == null && it.repetition == null }
            .sortedByDescending { it.stamps.createdAt }
    }
}

data class TaskTreeData(
    val tasks: List<Task>,
    val progress: Map<TaskId, ChildProgress>,
)

class ObserveTaskTreeUseCase @Inject constructor(
    private val tasks: TaskRepository,
) {
    /** @param includeFinished also load done and missed tasks, which are otherwise left out to keep the list short and fast */
    operator fun invoke(includeFinished: Boolean): Flow<TaskTreeData> =
        combine(
            if (includeFinished) tasks.observeAll() else tasks.observeOpen(),
            tasks.observeChildProgress(),
            ::TaskTreeData,
        )
}

/** Everything the task screen shows for one task. */
data class TaskDetail(
    val task: Task,
    val parent: Task?,
    val children: List<Task>,
    val childProgress: Map<TaskId, ChildProgress>,
    val checklist: List<ChecklistItem>,
    val notes: List<Note>,
    val attachments: List<Attachment>,
    val allTags: List<Tag>,
)

class ObserveTaskDetailUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val checklists: ChecklistRepository,
    private val notes: NoteRepository,
    private val attachments: AttachmentRepository,
    private val tags: TagRepository,
) {
    /** Emits null once the task no longer exists. */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(id: TaskId): Flow<TaskDetail?> {
        val task = tasks.observeTask(id)
        val parent = task.flatMapLatest { current ->
            current?.parentId?.let(tasks::observeTask) ?: flowOf(null)
        }
        val family = combine(task, parent, tasks.observeChildren(id), tasks.observeChildProgress()) { t, p, children, progress ->
            Family(t, p, children.sortedWith(TaskTree.treeOrder), progress)
        }
        return combine(
            family,
            checklists.observeFor(id),
            notes.observeFor(id),
            attachments.observeFor(id),
            tags.observeAll(),
        ) { f, checklist, noteList, photos, allTags ->
            f.task?.let {
                TaskDetail(
                    task = it,
                    parent = f.parent,
                    children = f.children,
                    childProgress = f.progress,
                    checklist = checklist.sortedBy { item -> item.position },
                    notes = noteList.sortedByDescending { note -> note.stamps.createdAt },
                    attachments = photos.sortedBy { photo -> photo.stamps.createdAt },
                    allTags = allTags,
                )
            }
        }
    }

    private data class Family(
        val task: Task?,
        val parent: Task?,
        val children: List<Task>,
        val progress: Map<TaskId, ChildProgress>,
    )
}

class SearchTasksUseCase @Inject constructor(
    private val tasks: TaskRepository,
) {
    suspend operator fun invoke(text: String): List<Task> {
        val query = SearchQuery.from(text) ?: return emptyList()
        return tasks.search(query)
    }
}

class SuggestTaskNamesUseCase @Inject constructor(
    private val tasks: TaskRepository,
) {
    /** Names used recently, offered as chips so a task done before can be added again without typing. */
    suspend operator fun invoke(limit: Int = 8): List<String> = tasks.recentNames(limit)
}
