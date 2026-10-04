package com.karan.anuj.core.domain.history

import com.karan.anuj.core.domain.place.PlaceRepository
import com.karan.anuj.core.domain.reminder.ReminderEvent
import com.karan.anuj.core.domain.reminder.ReminderRepository
import com.karan.anuj.core.domain.settings.SettingsRepository
import com.karan.anuj.core.domain.task.AttachmentFileStore
import com.karan.anuj.core.domain.task.PurgeTasksUseCase
import com.karan.anuj.core.domain.task.TASK_TABLE
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TaskRepository
import com.karan.anuj.core.domain.time.TimeSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * One line of the History screen.
 *
 * @property subject what was changed, by name: a task's name, or "Settings"
 * @property what the field in everyday words, with its old and new value
 */
data class HistoryLine(
    val subject: String,
    val what: String,
    val at: Long,
)

/** Turns stored changes into sentences. Stored values are column text, so they are tidied before being shown. */
object HistoryText {

    private val Moment = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
    private const val NOTHING = "nothing"

    /** "dueDate" is stored; "Due date" is read. */
    fun field(name: String): String =
        name.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase(Locale.ENGLISH).replaceFirstChar { it.uppercase() }

    /** A field ending in "At" holds a moment in milliseconds; everything else is shown as stored. */
    fun value(field: String, raw: String?, zone: ZoneId): String {
        if (raw.isNullOrEmpty()) return NOTHING
        val millis = raw.toLongOrNull()
        return if (field.endsWith("At") && millis != null) moment(millis, zone) else raw
    }

    fun moment(millis: Long, zone: ZoneId): String = Moment.format(Instant.ofEpochMilli(millis).atZone(zone)).lowercase(Locale.ENGLISH)

    fun describe(change: RecordChange, zone: ZoneId): String =
        "${field(change.field)}: ${value(change.field, change.oldValue, zone)} → ${value(change.field, change.newValue, zone)}"
}

class ObserveHistoryUseCase @Inject constructor(
    private val history: ChangeHistoryRepository,
    private val reminders: ReminderRepository,
    private val tasks: TaskRepository,
) {
    /** Every recorded edit of one task, newest first. */
    fun ofTask(id: TaskId, zone: ZoneId): Flow<List<HistoryLine>> =
        history.observeFor(TASK_TABLE, id.value).map { changes ->
            changes.map { HistoryLine(subject = "", what = HistoryText.describe(it, zone), at = it.changedAt) }
        }

    /** The latest edits to anything, newest first, each named after what was edited. */
    fun recentChanges(zone: ZoneId, limit: Int = RECENT): Flow<List<HistoryLine>> =
        history.observeRecent(limit).map { changes ->
            val names = changes.filter { it.table == TASK_TABLE }.map { it.rowId }.distinct()
                .associateWith { tasks.get(TaskId(it))?.name }
            changes.map { change ->
                val subject = when (change.table) {
                    TASK_TABLE -> names[change.rowId] ?: REMOVED_TASK
                    else -> change.table.replaceFirstChar { it.uppercase() }
                }
                HistoryLine(subject, HistoryText.describe(change, zone), change.changedAt)
            }
        }

    /** The latest reminders shown, held back, snoozed or answered, newest first. */
    fun recentReminders(limit: Int = RECENT): Flow<List<ReminderEvent>> = reminders.observeRecentEvents(limit)

    private companion object {
        /** Enough to scroll back through a few busy days without loading the whole log. */
        const val RECENT = 300
        const val REMOVED_TASK = "A task since removed"
    }
}

/**
 * The tidying the user has asked for in Settings, done each time the app
 * comes into view. With the defaults it does nothing at all: the trash is
 * kept until emptied by hand and the logs are kept always.
 */
class HouseKeepingUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val tasks: TaskRepository,
    private val purge: PurgeTasksUseCase,
    private val history: ChangeHistoryRepository,
    private val reminders: ReminderRepository,
    private val places: PlaceRepository,
    private val files: AttachmentFileStore,
    private val time: TimeSource,
) {
    suspend operator fun invoke() {
        val current = settings.settings.first()
        val now = time.nowMillis()

        current.emptyTrashAfterDays?.let { days ->
            val cutoff = now - days * MILLIS_PER_DAY
            purge(tasks.getTrash().filter { (it.stamps.deletedAt ?: now) < cutoff }.map { it.id })
        }
        current.keepHistoryDays?.let { days ->
            val cutoff = now - days * MILLIS_PER_DAY
            history.deleteBefore(cutoff)
            reminders.deleteEventsBefore(cutoff)
            places.deleteVisitsBefore(cutoff)
        }
        /** With "delete at once" nothing is ever set aside, and anything left from an earlier, longer choice goes now. */
        files.deleteSetAsideBefore(now - current.keepRemovedPhotosDays * MILLIS_PER_DAY)
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1_000
    }
}
