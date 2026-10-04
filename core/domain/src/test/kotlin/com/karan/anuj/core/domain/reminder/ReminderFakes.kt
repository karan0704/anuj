package com.karan.anuj.core.domain.reminder

import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.task.CompleteTaskUseCase
import com.karan.anuj.core.domain.task.MoveTaskToDayUseCase
import com.karan.anuj.core.domain.task.Repetition
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TaskWorld
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory stand-ins for storage and for the phone, so the reminder rules are tested without either. */

class FakeReminderRepository : ReminderRepository {
    private val rows = MutableStateFlow<Map<ReminderId, Reminder>>(emptyMap())
    private val log = MutableStateFlow<List<ReminderEvent>>(emptyList())

    val all: Collection<Reminder> get() = rows.value.values
    val events: List<ReminderEvent> get() = log.value
    fun reminder(id: String): Reminder = rows.value.getValue(ReminderId(id))

    private fun live(): Flow<List<Reminder>> = rows.map { it.values.filterNot { row -> row.stamps.isDeleted } }

    override fun observeFor(taskId: TaskId) = live().map { list -> list.filter { it.taskId == taskId } }
    override fun observeStanding() = live().map { list -> list.filter { it.isStanding } }
    override fun observeChanges(): Flow<Unit> = rows.map { }
    override fun observeEventsFor(id: ReminderId) = log.map { list -> list.filter { it.reminderId == id } }

    override suspend fun get(id: ReminderId): Reminder? = rows.value[id]
    override suspend fun getLive(): List<Reminder> = all.filterNot { it.stamps.isDeleted }
    override suspend fun taskIdsEverReminded(): Set<TaskId> = all.mapNotNull { it.taskId }.toSet()
    override suspend fun save(reminders: List<Reminder>) = rows.update { it + reminders.associateBy { row -> row.id } }
    override suspend fun log(event: ReminderEvent) = log.update { it + event }
    override suspend fun eventsSince(fromMillis: Long) = events.filter { it.at >= fromMillis }.sortedBy { it.at }
    override suspend fun lastEventAt(kind: ReminderEventKind) = events.filter { it.kind == kind }.maxOfOrNull { it.at }
}

class FakeReminderSettings(initial: ReminderSettings = ReminderSettings()) : ReminderSettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<ReminderSettings> = state
    override suspend fun update(change: (ReminderSettings) -> ReminderSettings) = state.update(change)
}

class FakeNotifier : ReminderNotifier {
    val shown = mutableListOf<ReminderNotice>()
    val cancelled = mutableListOf<ReminderId>()
    val summaries = mutableListOf<List<String>>()

    override fun show(notice: ReminderNotice) {
        shown += notice
    }

    override fun cancel(id: ReminderId) {
        cancelled += id
    }

    override fun showSummary(titles: List<String>, settings: CategorySettings) {
        summaries += titles
    }
}

class FakeAlarms : AlarmGateway {
    var wakeAt: Long? = null
    var asAlarmClock = false

    override fun setNext(atMillis: Long?, asAlarmClock: Boolean) {
        wakeAt = atMillis
        this.asAlarmClock = asAlarmClock
    }
}

/** The zone the reminder tests run in. India has no daylight saving, so clock arithmetic in a test reads as written. */
val INDIA: ZoneId = ZoneId.of("Asia/Kolkata")

/** The instant a clock time on a day falls at, in [zone]. */
fun moment(date: LocalDate, hour: Int, minute: Int = 0, zone: ZoneId = INDIA): Long =
    date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

/** Everything a reminder test needs, wired together on top of the task test world. */
class ReminderWorld(settings: ReminderSettings = ReminderSettings(autoRemindTimedTasks = false)) {
    val world = TaskWorld()
    val clock = world.clock
    val reminders = FakeReminderRepository()
    val settings = FakeReminderSettings(settings)
    val notifier = FakeNotifier()
    val alarms = FakeAlarms()
    private val zones = ZoneSource { INDIA }

    val sync = SyncRemindersUseCase(
        reminders, world.tasks, this.settings, notifier, alarms, zones, clock, world.ids,
    )
    val answer = AnswerReminderUseCase(
        reminders,
        world.tasks,
        CompleteTaskUseCase(world.tasks, world.editor, world.ids),
        MoveTaskToDayUseCase(world.tasks, world.editor),
        notifier,
        sync,
        zones,
        clock,
        world.ids,
    )
    val taskReminders = TaskRemindersUseCase(reminders, world.ids, clock)
    val standing = StandingRemindersUseCase(reminders, world.ids, clock)

    suspend fun givenTask(
        id: String,
        due: LocalDate?,
        time: LocalTime? = null,
        repetition: Repetition? = null,
    ): Task {
        val task = Task(
            id = TaskId(id),
            name = id,
            dueDate = due,
            dueTime = time,
            repetition = repetition,
            stamps = RecordStamps.created(clock.now),
        )
        world.tasks.save(listOf(task))
        return task
    }

    suspend fun givenReminder(
        id: String,
        task: String? = null,
        schedule: ReminderSchedule = ReminderSchedule.BeforeTask(),
        nagging: Nagging? = null,
        category: ReminderCategory = ReminderCategory.TASK,
        style: ReminderStyle = ReminderStyle.NOTIFICATION,
    ): Reminder {
        val reminder = Reminder(
            id = ReminderId(id),
            taskId = task?.let(::TaskId),
            title = id,
            schedule = schedule,
            category = category,
            style = style,
            nagging = nagging,
            stamps = RecordStamps.created(clock.now),
        )
        reminders.save(listOf(reminder))
        return reminder
    }

    /** Moves the clock to [now] and runs one sync, as the alarm going off would. */
    suspend fun syncAt(now: Long) {
        clock.now = now
        sync()
    }
}
