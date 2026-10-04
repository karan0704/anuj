package com.karan.anuj.core.data.reminder

import com.karan.anuj.core.data.db.ReminderDao
import com.karan.anuj.core.data.db.ReminderEntity
import com.karan.anuj.core.data.db.ReminderEventEntity
import com.karan.anuj.core.data.db.StampColumns
import com.karan.anuj.core.data.di.IoDispatcher
import com.karan.anuj.core.domain.reminder.Nagging
import com.karan.anuj.core.domain.reminder.Reminder
import com.karan.anuj.core.domain.reminder.ReminderCategory
import com.karan.anuj.core.domain.reminder.ReminderEvent
import com.karan.anuj.core.domain.reminder.ReminderEventKind
import com.karan.anuj.core.domain.reminder.ReminderId
import com.karan.anuj.core.domain.reminder.ReminderRepository
import com.karan.anuj.core.domain.reminder.ReminderScheduleCodec
import com.karan.anuj.core.domain.reminder.ReminderState
import com.karan.anuj.core.domain.reminder.ReminderStyle
import com.karan.anuj.core.domain.task.TaskId
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * A row whose schedule this version cannot read (written by a newer
 * version) is skipped rather than guessed at: a reminder at the wrong time
 * is worse than one that waits for the app to be updated.
 */
internal fun ReminderEntity.toDomain(): Reminder? {
    val schedule = ReminderScheduleCodec.decode(schedule) ?: return null
    return Reminder(
        id = ReminderId(id),
        taskId = taskId?.let(::TaskId),
        title = title,
        schedule = schedule,
        category = ReminderCategory.entries.firstOrNull { it.name == category } ?: ReminderCategory.TASK,
        style = ReminderStyle.entries.firstOrNull { it.name == style } ?: ReminderStyle.NOTIFICATION,
        nagging = nagEveryMinutes?.let { Nagging(everyMinutes = it, times = nagTimes) },
        toneUri = toneUri,
        enabled = enabled,
        state = ReminderState(lastOccurrenceAt, lastFiredAt, nagsSent, snoozedUntil, answeredAt),
        stamps = stamps.toDomain(),
    )
}

internal fun Reminder.toEntity() = ReminderEntity(
    id = id.value,
    taskId = taskId?.value,
    title = title,
    schedule = ReminderScheduleCodec.encode(schedule),
    category = category.name,
    style = style.name,
    nagEveryMinutes = nagging?.everyMinutes,
    nagTimes = nagging?.times ?: 0,
    toneUri = toneUri,
    enabled = enabled,
    lastOccurrenceAt = state.lastOccurrenceAt,
    lastFiredAt = state.lastFiredAt,
    nagsSent = state.nagsSent,
    snoozedUntil = state.snoozedUntil,
    answeredAt = state.answeredAt,
    stamps = StampColumns.from(stamps),
)

/** A log line of a kind this version does not know is skipped. */
internal fun ReminderEventEntity.toDomain(): ReminderEvent? {
    val kind = ReminderEventKind.entries.firstOrNull { it.name == kind } ?: return null
    return ReminderEvent(id, reminderId?.let(::ReminderId), taskId?.let(::TaskId), title, kind, at, minutes, reason)
}

internal fun ReminderEvent.toEntity() =
    ReminderEventEntity(id, reminderId?.value, taskId?.value, title, kind.name, at, minutes, reason)

/**
 * The DAO is taken lazily and only touched on the IO dispatcher: the first
 * use opens the encrypted database.
 */
class RoomReminderRepository @Inject constructor(
    private val dao: Lazy<ReminderDao>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ReminderRepository {

    private fun <T> onIo(query: (ReminderDao) -> Flow<T>): Flow<T> = flow { emitAll(query(dao.get())) }.flowOn(io)

    override fun observeFor(taskId: TaskId): Flow<List<Reminder>> =
        onIo { dao -> dao.observeFor(taskId.value).map { rows -> rows.mapNotNull { it.toDomain() } } }

    override fun observeStanding(): Flow<List<Reminder>> =
        onIo { dao -> dao.observeStanding().map { rows -> rows.mapNotNull { it.toDomain() } } }

    override fun observeChanges(): Flow<Unit> = onIo { dao -> dao.observeChangeTick().map { } }

    override fun observeEventsFor(id: ReminderId): Flow<List<ReminderEvent>> =
        onIo { dao -> dao.observeEventsFor(id.value).map { rows -> rows.mapNotNull { it.toDomain() } } }

    override suspend fun get(id: ReminderId): Reminder? = withContext(io) { dao.get().get(id.value)?.toDomain() }

    override suspend fun getLive(): List<Reminder> = withContext(io) { dao.get().getLive().mapNotNull { it.toDomain() } }

    override suspend fun taskIdsEverReminded(): Set<TaskId> =
        withContext(io) { dao.get().taskIdsEverReminded().map(::TaskId).toSet() }

    override suspend fun save(reminders: List<Reminder>) = withContext(io) {
        dao.get().upsert(reminders.map { it.toEntity() })
    }

    override suspend fun log(event: ReminderEvent) = withContext(io) { dao.get().insertEvent(event.toEntity()) }

    override suspend fun eventsSince(fromMillis: Long): List<ReminderEvent> =
        withContext(io) { dao.get().eventsSince(fromMillis).mapNotNull { it.toDomain() } }

    override suspend fun lastEventAt(kind: ReminderEventKind): Long? = withContext(io) { dao.get().lastEventAt(kind.name) }

    override fun observeRecentEvents(limit: Int): Flow<List<ReminderEvent>> =
        onIo { dao -> dao.observeRecentEvents(limit).map { rows -> rows.mapNotNull { it.toDomain() } } }

    override suspend fun deleteEventsBefore(millis: Long) = withContext(io) { dao.get().deleteEventsBefore(millis) }
}
