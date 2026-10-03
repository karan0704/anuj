package com.karan.anuj.core.domain.reminder

import com.karan.anuj.core.domain.task.TaskId
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** The phone's time zone. Behind an interface so the date maths can be tested in any zone. */
fun interface ZoneSource {
    fun zone(): ZoneId
}

/** Storage for reminders and their log. Reminders in the trash are left out unless a function says otherwise. */
interface ReminderRepository {

    fun observeFor(taskId: TaskId): Flow<List<Reminder>>

    /** Reminders that stand on their own, without a task. */
    fun observeStanding(): Flow<List<Reminder>>

    /** Emits whenever a reminder or a task has changed, which is when the next alarm may have moved. */
    fun observeChanges(): Flow<Unit>

    fun observeEventsFor(id: ReminderId): Flow<List<ReminderEvent>>

    suspend fun get(id: ReminderId): Reminder?

    /** Every reminder that is not in the trash, switched on or not. */
    suspend fun getLive(): List<Reminder>

    /** Tasks that have ever had a reminder set, including reminders since removed. */
    suspend fun taskIdsEverReminded(): Set<TaskId>

    suspend fun save(reminders: List<Reminder>)

    suspend fun log(event: ReminderEvent)

    /** Log lines at or after [fromMillis], oldest first. */
    suspend fun eventsSince(fromMillis: Long): List<ReminderEvent>

    suspend fun lastEventAt(kind: ReminderEventKind): Long?
}

/** Asks the phone to wake the app at a moment, with the app closed. Only one moment is held at a time. */
interface AlarmGateway {

    /**
     * @param atMillis null cancels the wake-up
     * @param asAlarmClock the reminder due then is a full-screen alarm, which
     * the phone must never delay
     */
    fun setNext(atMillis: Long?, asAlarmClock: Boolean)
}

/** Puts reminders in front of the user. */
interface ReminderNotifier {
    fun show(notice: ReminderNotice)
    fun cancel(id: ReminderId)

    /** One notification listing everything that was held back. */
    fun showSummary(titles: List<String>, settings: CategorySettings)
}
