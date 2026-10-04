package com.karan.anuj.core.domain.reminder

import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.task.IdGenerator
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TaskRepository
import com.karan.anuj.core.domain.task.TaskTree
import com.karan.anuj.core.domain.time.TimeSource
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one step that keeps reminders true: show everything that has come
 * due, then ask the phone to wake the app at the next due moment.
 *
 * It is safe to run at any time and any number of times. Everything that
 * could change what is due (an alarm going off, a restart, a clock or zone
 * change, a task or reminder being edited) simply runs it again.
 */
@Singleton
class SyncRemindersUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val tasks: TaskRepository,
    private val settingsRepository: ReminderSettingsRepository,
    private val notifier: ReminderNotifier,
    private val alarms: AlarmGateway,
    private val zones: ZoneSource,
    private val time: TimeSource,
    private val ids: IdGenerator,
) {
    /** Two runs at once could show the same reminder twice. */
    private val oneAtATime = Mutex()

    suspend operator fun invoke() = oneAtATime.withLock {
        val now = time.nowMillis()
        val zone = zones.zone()
        val settings = settingsRepository.settings.first()
        val openTasks = tasks.getOpen().associateBy { it.id }

        addAutomaticReminders(openTasks.values, settings, now)

        val run = Run(now, zone, settings, openTasks, shownToday = countShownToday(now, zone))
        val after = reminders.getLive().map { reminder -> run.settle(reminder) }
        val changed = after.filter { it.changed }.map { it.reminder }
        if (changed.isNotEmpty()) reminders.save(changed)

        val summaryAt = showOrPlanSummary(run)
        val nextFire = after.mapNotNull { it.next }.minByOrNull { it.at }
        val wakeAt = listOfNotNull(nextFire?.at, summaryAt).minOrNull()
        alarms.setNext(wakeAt, asAlarmClock = nextFire != null && nextFire.at == wakeAt && nextFire.isAlarm)
    }

    /** A reminder after one run, and when it is due next. */
    private class Settled(val reminder: Reminder, val changed: Boolean, val next: NextFire?)

    private class NextFire(val at: Long, val isAlarm: Boolean)

    /** The facts of one run, and the work done on each reminder within it. */
    private inner class Run(
        val now: Long,
        val zone: ZoneId,
        val settings: ReminderSettings,
        val openTasks: Map<TaskId, Task>,
        var shownToday: Int,
    ) {
        val clockTime = Instant.ofEpochMilli(now).atZone(zone).toLocalTime()

        suspend fun settle(original: Reminder): Settled {
            val context = ScheduleContext(zone, original.taskId?.let(openTasks::get), settings.allDayTime)
            var reminder = withdrawIfLeftBehind(original, context)
            ReminderPlanner.next(reminder, context)?.takeIf { it.at <= now }?.let { due ->
                reminder = fire(reminder, due, context.task)
            }
            val next = ReminderPlanner.next(reminder, context)
            return Settled(reminder, changed = reminder != original, next?.let { NextFire(it.at, reminder.style == ReminderStyle.ALARM) })
        }

        /**
         * A task's reminder that showed and was never answered, while the
         * task has since been finished, trashed or moved to a later time:
         * its notification is taken down, as nobody is waiting on it any more.
         */
        private fun withdrawIfLeftBehind(reminder: Reminder, context: ScheduleContext): Reminder {
            if (reminder.isStanding || !reminder.state.isWaitingForAnswer) return reminder
            val next = ReminderPlanner.next(reminder, context)
            val taskClosed = context.task == null
            val taskMovedOn = next is PlannedFire.Occurrence && next.at > now
            if (!taskClosed && !taskMovedOn) return reminder
            notifier.cancel(reminder.id)
            return reminder.copy(state = reminder.state.copy(answeredAt = now))
        }

        private suspend fun fire(reminder: Reminder, due: PlannedFire, task: Task?): Reminder {
            val category = settings.category(reminder.category)
            val verdict = NotificationPolicy.decide(category, settings, shownToday, clockTime)
            val isRepeat = due is PlannedFire.Nag
            val title = task?.name ?: reminder.title

            if (verdict == Verdict.SHOW) {
                notifier.show(noticeFor(reminder, task, title, category, isRepeat))
                if (category.countsTowardLimit) shownToday++
            }
            eventKindFor(verdict, isRepeat)?.let { kind ->
                reminders.log(ReminderEvent(ids.newId(), reminder.id, reminder.taskId, title, kind, at = now))
            }

            /** Every planned time up to now is used up by this one showing, so times missed while the phone was off do not arrive in a burst. */
            val state = reminder.state
            return reminder.copy(
                state = state.copy(
                    lastOccurrenceAt = now,
                    lastFiredAt = now,
                    nagsSent = if (isRepeat) state.nagsSent + 1 else 0,
                    snoozedUntil = null,
                ),
            )
        }

        private suspend fun noticeFor(
            reminder: Reminder,
            task: Task?,
            title: String,
            category: CategorySettings,
            isRepeat: Boolean,
        ) = ReminderNotice(
            reminderId = reminder.id,
            taskId = reminder.taskId,
            title = title,
            category = reminder.category,
            settings = category,
            style = reminder.style,
            toneUri = reminder.toneUri,
            leadMinutes = (reminder.schedule as? ReminderSchedule.BeforeTask)?.leadMinutes ?: 0,
            isRepeat = isRepeat,
            bring = task?.let { tasks.getSubtree(it.id) }.orEmpty()
                .filter { it.parentId == task?.id && it.isOpen && !it.stamps.isDeleted }
                .sortedWith(TaskTree.treeOrder)
                .map { it.name },
            snoozeMinutes = settings.defaultSnoozeMinutes,
        )
    }

    /** A held repeat is not logged again: the summary should name a reminder once. */
    private fun eventKindFor(verdict: Verdict, isRepeat: Boolean): ReminderEventKind? = when (verdict) {
        Verdict.SHOW -> if (isRepeat) ReminderEventKind.NAGGED else ReminderEventKind.SHOWN
        Verdict.HOLD -> ReminderEventKind.HELD.takeIf { !isRepeat }
        Verdict.DROP -> null
    }

    /**
     * A task that has a day and a time but has never had a reminder set
     * gets one at its time, so "remember this at 6" needs no extra taps. A
     * task whose reminders the user removed is left alone: the removed rows
     * are what record that choice.
     */
    private suspend fun addAutomaticReminders(openTasks: Collection<Task>, settings: ReminderSettings, now: Long) {
        if (!settings.autoRemindTimedTasks) return
        val alreadySet = reminders.taskIdsEverReminded()
        val added = openTasks
            .filter { it.dueDate != null && it.dueTime != null && it.id !in alreadySet }
            .map { task ->
                Reminder(
                    id = ReminderId(ids.newId()),
                    taskId = task.id,
                    schedule = ReminderSchedule.BeforeTask(),
                    nagging = settings.defaultNagging,
                    stamps = RecordStamps.created(now),
                )
            }
        if (added.isNotEmpty()) reminders.save(added)
    }

    private suspend fun countShownToday(now: Long, zone: ZoneId): Int {
        val dayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        return reminders.eventsSince(dayStart).count { it.kind == ReminderEventKind.SHOWN || it.kind == ReminderEventKind.NAGGED }
    }

    /**
     * Shows the summary of held reminders if its time has come, and returns
     * the moment the app should next wake for a summary, or null when
     * nothing is being held.
     */
    private suspend fun showOrPlanSummary(run: Run): Long? {
        val settings = run.settings
        val category = settings.category(ReminderCategory.SUMMARY)
        if (!category.enabled) return null

        val lastSummaryAt = reminders.lastEventAt(ReminderEventKind.SUMMARY) ?: 0L
        val held = reminders.eventsSince(lastSummaryAt).filter { it.kind == ReminderEventKind.HELD }
        val firstHeldAt = held.firstOrNull()?.at ?: return null

        /** The end of quiet hours is a summary time too: that is when what was held overnight should be seen. */
        val quiet = settings.quietHours
        val times = settings.summaryTimes + listOfNotNull(quiet.until.takeIf { quiet.enabled })
        val schedule = ReminderSchedule.AtTimes(times)
        val context = ScheduleContext(run.zone, task = null, allDayTime = settings.allDayTime)

        val dueAt = schedule.nextOccurrence(after = firstHeldAt, context) ?: return null
        if (dueAt > run.now) return dueAt
        if (quiet.covers(run.clockTime) || settings.calmMode) return schedule.nextOccurrence(after = run.now, context)

        notifier.showSummary(held.map { it.title }.distinct(), category)
        reminders.log(ReminderEvent(ids.newId(), title = "", kind = ReminderEventKind.SUMMARY, at = run.now))
        return null
    }
}
