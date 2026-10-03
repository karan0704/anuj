package com.karan.anuj.core.domain.reminder

import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.task.CompleteTaskUseCase
import com.karan.anuj.core.domain.task.IdGenerator
import com.karan.anuj.core.domain.task.MoveTaskToDayUseCase
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TaskRepository
import com.karan.anuj.core.domain.time.TimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * A task's reminders as the user thinks of them: "at the time and 15
 * minutes before, keep reminding me, as an alarm". Stored as one reminder
 * per time; the one closest to the task's time is the main one and carries
 * the repeat and the style, the earlier ones are plain early warnings.
 *
 * @property leads minutes before the task's time; empty means no reminder
 */
data class TaskReminderPlan(
    val leads: Set<Int> = emptySet(),
    val nagging: Nagging? = null,
    val style: ReminderStyle = ReminderStyle.NOTIFICATION,
    val toneUri: String? = null,
)

class TaskRemindersUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val ids: IdGenerator,
    private val time: TimeSource,
) {
    fun observePlan(taskId: TaskId): Flow<TaskReminderPlan> = reminders.observeFor(taskId).map(::planOf)

    private fun planOf(rows: List<Reminder>): TaskReminderPlan {
        val byLead = rows.filter { it.enabled }.mapNotNull { row ->
            (row.schedule as? ReminderSchedule.BeforeTask)?.let { it.leadMinutes to row }
        }
        val main = byLead.minByOrNull { it.first }?.second ?: return TaskReminderPlan()
        return TaskReminderPlan(byLead.map { it.first }.toSet(), main.nagging, main.style, main.toneUri)
    }

    /**
     * Makes the stored reminders match [plan]. A time no longer wanted is
     * moved to the trash rather than erased: that row is what tells the app
     * the user chose not to be reminded, so no automatic reminder comes back.
     */
    suspend fun setPlan(taskId: TaskId, plan: TaskReminderPlan) {
        val now = time.nowMillis()
        val existing = reminders.observeFor(taskId).first()
            .mapNotNull { row -> (row.schedule as? ReminderSchedule.BeforeTask)?.let { it.leadMinutes to row } }
            .toMap()
        val mainLead = plan.leads.minOrNull()

        val kept = plan.leads.map { lead ->
            val isMain = lead == mainLead
            val base = existing[lead] ?: Reminder(
                id = ReminderId(ids.newId()),
                taskId = taskId,
                schedule = ReminderSchedule.BeforeTask(lead),
                stamps = RecordStamps.created(now),
            )
            base.copy(
                enabled = true,
                category = when {
                    !isMain -> ReminderCategory.HEADS_UP
                    plan.style == ReminderStyle.ALARM -> ReminderCategory.ALARM
                    else -> ReminderCategory.TASK
                },
                style = if (isMain) plan.style else ReminderStyle.NOTIFICATION,
                nagging = plan.nagging.takeIf { isMain },
                toneUri = plan.toneUri,
                stamps = base.stamps.touched(now),
            )
        }
        val removed = existing.filterKeys { it !in plan.leads }.values.map { it.copy(stamps = it.stamps.deleted(now)) }
        reminders.save(kept + removed)
    }
}

/** What the user supplies for a reminder that stands on its own. */
data class StandingReminderDraft(
    val title: String,
    val schedule: ReminderSchedule,
    val nagging: Nagging? = null,
    val style: ReminderStyle = ReminderStyle.NOTIFICATION,
    val toneUri: String? = null,
)

/**
 * Ready-made regular reminders. Their names become the user's own data,
 * free to rename, so they live with the rules rather than the screen labels.
 */
object StandingPresets {
    val medication = StandingReminderDraft(
        title = "Take medication",
        schedule = ReminderSchedule.AtTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
        nagging = Nagging(everyMinutes = 10, times = 3),
    )
    val water = StandingReminderDraft(
        title = "Drink water",
        schedule = ReminderSchedule.Every(everyMinutes = 120, from = LocalTime.of(9, 0), until = LocalTime.of(21, 0)),
    )
    val meals = StandingReminderDraft(
        title = "Time to eat",
        schedule = ReminderSchedule.AtTimes(listOf(LocalTime.of(8, 30), LocalTime.of(13, 0), LocalTime.of(20, 0))),
    )
    val custom = StandingReminderDraft(
        title = "Reminder",
        schedule = ReminderSchedule.AtTimes(listOf(LocalTime.of(9, 0))),
    )
}

class StandingRemindersUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val ids: IdGenerator,
    private val time: TimeSource,
) {
    /** One-off reminders (a test, "in an hour") are not regular reminders and are left out. */
    fun observe(): Flow<List<Reminder>> =
        reminders.observeStanding().map { rows -> rows.filter { it.schedule !is ReminderSchedule.Once } }

    suspend fun add(draft: StandingReminderDraft, category: ReminderCategory = ReminderCategory.HEALTH): Reminder {
        val reminder = Reminder(
            id = ReminderId(ids.newId()),
            title = draft.title.trim(),
            schedule = draft.schedule,
            category = if (draft.style == ReminderStyle.ALARM) ReminderCategory.ALARM else category,
            style = draft.style,
            nagging = draft.nagging,
            toneUri = draft.toneUri,
            stamps = RecordStamps.created(time.nowMillis()),
        )
        reminders.save(listOf(reminder))
        return reminder
    }

    /**
     * Saves an edit. Its times are counted from now on, so changing "8:00"
     * to "7:00" at noon does not make the 7:00 of this morning go off.
     */
    suspend fun update(id: ReminderId, change: (Reminder) -> Reminder) {
        val current = reminders.get(id) ?: return
        val now = time.nowMillis()
        val edited = change(current)
        if (edited == current) return
        val category = when {
            edited.style == ReminderStyle.ALARM -> ReminderCategory.ALARM
            current.category == ReminderCategory.ALARM -> ReminderCategory.HEALTH
            else -> edited.category
        }
        reminders.save(
            listOf(
                edited.copy(
                    category = category,
                    state = ReminderState(lastOccurrenceAt = now, answeredAt = now),
                    stamps = edited.stamps.touched(now),
                ),
            ),
        )
    }

    suspend fun remove(reminder: Reminder) =
        reminders.save(listOf(reminder.copy(stamps = reminder.stamps.deleted(time.nowMillis()))))

    suspend fun restore(reminder: Reminder) =
        reminders.save(listOf(reminder.copy(stamps = reminder.stamps.restored(time.nowMillis()))))
}

/** What the user can do with a reminder that is showing. Each ends by re-planning the next alarm. */
class AnswerReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val tasks: TaskRepository,
    private val completeTask: CompleteTaskUseCase,
    private val moveTask: MoveTaskToDayUseCase,
    private val notifier: ReminderNotifier,
    private val sync: SyncRemindersUseCase,
    private val zones: ZoneSource,
    private val time: TimeSource,
    private val ids: IdGenerator,
) {
    /** A task's reminder finishes the task; a standing reminder is just marked as answered until its next time. */
    suspend fun done(id: ReminderId) = answer(id, ReminderEventKind.DONE) { reminder, _ ->
        reminder.taskId?.let { completeTask(it, today()) }
    }

    /** @param reason why, when the user said; kept in the log */
    suspend fun snooze(id: ReminderId, minutes: Int, reason: String? = null) {
        val reminder = reminders.get(id) ?: return
        val now = time.nowMillis()
        val until = now + minutes.coerceAtLeast(1) * ReminderPlanner.MILLIS_PER_MINUTE
        reminders.save(listOf(reminder.copy(state = reminder.state.copy(snoozedUntil = until, answeredAt = now))))
        log(reminder, ReminderEventKind.SNOOZED, now, minutes, reason?.trim()?.takeIf { it.isNotEmpty() })
        notifier.cancel(id)
        sync()
    }

    /** Puts the task off until tomorrow. It is the user's choice, so it does not count as a carry. */
    suspend fun moveToTomorrow(id: ReminderId) = answer(id, ReminderEventKind.MOVED) { reminder, _ ->
        reminder.taskId?.let { moveTask(it, today().plusDays(1), countsAsCarry = false) }
    }

    /** The title to show for a reminder: its task's name, or its own. Null when it no longer exists. */
    suspend fun titleOf(id: ReminderId): String? {
        val reminder = reminders.get(id) ?: return null
        return reminder.taskId?.let { tasks.get(it)?.name } ?: reminder.title
    }

    private suspend fun answer(id: ReminderId, kind: ReminderEventKind, action: suspend (Reminder, Long) -> Unit) {
        val reminder = reminders.get(id) ?: return
        val now = time.nowMillis()
        action(reminder, now)
        /** Every reminder of the same task is answered together, so an early warning does not linger after the task is done. */
        val answered = siblingsOf(reminder).map { it.copy(state = it.state.copy(answeredAt = now, snoozedUntil = null)) }
        reminders.save(answered)
        answered.forEach { notifier.cancel(it.id) }
        log(reminder, kind, now)
        sync()
    }

    private suspend fun siblingsOf(reminder: Reminder): List<Reminder> =
        if (reminder.taskId == null) listOf(reminder) else reminders.observeFor(reminder.taskId).first()

    private suspend fun log(reminder: Reminder, kind: ReminderEventKind, now: Long, minutes: Int? = null, reason: String? = null) {
        val title = reminder.taskId?.let { tasks.get(it)?.name } ?: reminder.title
        reminders.log(ReminderEvent(ids.newId(), reminder.id, reminder.taskId, title, kind, now, minutes, reason))
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(time.nowMillis()).atZone(zones.zone()).toLocalDate()
}

/**
 * Sends a real reminder through the real alarm, a short while from now, so
 * the user can close the app and see whether this phone lets it through.
 */
class ReminderTestUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val sync: SyncRemindersUseCase,
    private val ids: IdGenerator,
    private val time: TimeSource,
) {
    /** @return the test reminder, to watch for its arrival */
    suspend fun start(title: String, delaySeconds: Int): Reminder {
        val now = time.nowMillis()
        val reminder = Reminder(
            id = ReminderId(ids.newId()),
            title = title,
            schedule = ReminderSchedule.Once(now + delaySeconds * MILLIS_PER_SECOND),
            stamps = RecordStamps.created(now),
        )
        reminders.save(listOf(reminder))
        sync()
        return reminder
    }

    /** How late the test arrived, in seconds; null until it has. */
    fun observeDelay(reminder: Reminder): Flow<Long?> = reminders.observeEventsFor(reminder.id).map { events ->
        val plannedAt = (reminder.schedule as? ReminderSchedule.Once)?.at ?: return@map null
        events.firstOrNull { it.kind != ReminderEventKind.DONE }?.let { (it.at - plannedAt).coerceAtLeast(0) / MILLIS_PER_SECOND }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/** Reading and changing the reminder settings. */
class ReminderSettingsUseCase @Inject constructor(
    private val repository: ReminderSettingsRepository,
) {
    fun observe(): Flow<ReminderSettings> = repository.settings

    /**
     * Values are kept inside their allowed ranges and the chip lists are
     * kept in order, without repeats and never empty, so a slip in a screen
     * cannot store settings that leave the user with nothing to tap.
     */
    suspend fun update(change: (ReminderSettings) -> ReminderSettings) = repository.update { current ->
        val next = change(current)
        next.copy(
            dailyLimit = next.dailyLimit.coerceIn(ReminderSettings.DAILY_LIMIT_RANGE),
            routineWarningMinutes = next.routineWarningMinutes.coerceIn(ReminderSettings.ROUTINE_WARNING_RANGE),
            leadChoices = next.leadChoices.cleaned(orElse = current.leadChoices),
            snoozeChoices = next.snoozeChoices.cleaned(orElse = current.snoozeChoices),
            nagChoices = next.nagChoices.cleaned(orElse = current.nagChoices),
            summaryTimes = next.summaryTimes.distinct().sorted(),
            snoozeReasons = next.snoozeReasons.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            defaultNagging = next.defaultNagging?.let { it.copy(times = it.times.coerceIn(ReminderSettings.NAG_TIMES_RANGE)) },
        )
    }

    private fun List<Int>.cleaned(orElse: List<Int>): List<Int> =
        filter { it >= 0 }.distinct().sorted().ifEmpty { orElse }
}
