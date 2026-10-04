package com.karan.anuj.core.domain.reminder

import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.task.TaskId

@JvmInline
value class ReminderId(val value: String)

/** How a reminder shows itself: a normal notification, or an alarm that takes over the screen and keeps ringing. */
enum class ReminderStyle { NOTIFICATION, ALARM }

/** Repeats a reminder that has not been answered: every [everyMinutes], at most [times] more times. */
data class Nagging(val everyMinutes: Int, val times: Int)

/**
 * What has happened to a reminder so far. Kept apart from what the user set
 * up, so the engine's bookkeeping never looks like an edit.
 *
 * @property lastOccurrenceAt every planned time up to this moment has been dealt with
 * @property lastFiredAt when it last showed (first time, repeat, or after a snooze)
 * @property nagsSent repeats sent since it last showed for the first time
 * @property snoozedUntil set while the user has asked to be reminded again later
 * @property answeredAt when the user last answered it; an answer newer than
 * [lastFiredAt] stops the repeats
 */
data class ReminderState(
    val lastOccurrenceAt: Long? = null,
    val lastFiredAt: Long? = null,
    val nagsSent: Int = 0,
    val snoozedUntil: Long? = null,
    val answeredAt: Long? = null,
) {
    /** It has shown and the user has not answered since. */
    val isWaitingForAnswer: Boolean
        get() = lastFiredAt != null && (answeredAt ?: Long.MIN_VALUE) < lastFiredAt
}

/**
 * One reminder. It either belongs to a task ([taskId] set, timed from the
 * task's day and time) or stands on its own ([taskId] null, with its own
 * [title] and clock times), as for medication, water and meals.
 *
 * @property toneUri the sound for this reminder alone; null uses its category's tone
 */
data class Reminder(
    val id: ReminderId,
    val taskId: TaskId? = null,
    val title: String = "",
    val schedule: ReminderSchedule,
    val category: ReminderCategory = ReminderCategory.TASK,
    val style: ReminderStyle = ReminderStyle.NOTIFICATION,
    val nagging: Nagging? = null,
    val toneUri: String? = null,
    val enabled: Boolean = true,
    val state: ReminderState = ReminderState(),
    val stamps: RecordStamps,
) {
    val isStanding: Boolean get() = taskId == null
}

enum class ReminderEventKind {
    /** Shown for the first time for a planned time. */
    SHOWN,

    /** Shown again because it was not answered. */
    NAGGED,

    /** Not shown when it was due (quiet hours, calm mode, daily limit); waits for the next summary. */
    HELD,
    SNOOZED,
    DONE,

    /** Moved to another day from the reminder. */
    MOVED,

    /** The summary of held reminders was shown. */
    SUMMARY,
}

/**
 * One line of the reminder log. The title is copied in, so the log still
 * reads after the task or reminder it came from is gone.
 *
 * @property minutes how long a snooze was for
 * @property reason why it was snoozed, when the user said
 */
data class ReminderEvent(
    val id: String,
    val reminderId: ReminderId? = null,
    val taskId: TaskId? = null,
    val title: String,
    val kind: ReminderEventKind,
    val at: Long,
    val minutes: Int? = null,
    val reason: String? = null,
)

/**
 * What the phone is asked to show for one reminder.
 *
 * @property leadMinutes how long before the task's time this is; zero means "now"
 * @property isRepeat true when this is a repeat of one already shown
 * @property bring the task's unticked checklist lines: the things to have ready
 * @property snoozeMinutes the length of the one-tap snooze
 */
data class ReminderNotice(
    val reminderId: ReminderId,
    val taskId: TaskId?,
    val title: String,
    val category: ReminderCategory,
    val settings: CategorySettings,
    val style: ReminderStyle,
    val toneUri: String?,
    val leadMinutes: Int = 0,
    val isRepeat: Boolean = false,
    val bring: List<String> = emptyList(),
    val snoozeMinutes: Int,
)
