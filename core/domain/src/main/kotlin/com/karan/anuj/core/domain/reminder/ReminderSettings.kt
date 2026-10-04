package com.karan.anuj.core.domain.reminder

import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

/** Whether a kind of notification interrupts when it is due or waits for the next summary. */
enum class Delivery { NOW, SUMMARY }

/**
 * The user's rules for one kind of notification.
 *
 * @property toneUri the sound; null uses the phone's default
 * @property breaksQuiet still shown during quiet hours and in calm mode
 * @property countsTowardLimit counted against the daily limit, and held once the limit is reached
 */
data class CategorySettings(
    val enabled: Boolean = true,
    val delivery: Delivery = Delivery.NOW,
    val toneUri: String? = null,
    val vibrate: Boolean = true,
    val breaksQuiet: Boolean = false,
    val countsTowardLimit: Boolean = true,
)

/**
 * The kinds of notification the app sends. Every reminder belongs to one, so
 * each kind can be silenced, given its own tone, or moved to the summary
 * without touching the others.
 *
 * @property defaults how the kind behaves until the user changes it
 */
enum class ReminderCategory(val defaults: CategorySettings) {
    /** A task's own reminder. */
    TASK(CategorySettings()),

    /** A full-screen alarm. Rings through quiet hours and is not counted against the limit, or it would not be an alarm. */
    ALARM(CategorySettings(breaksQuiet = true, countsTowardLimit = false)),

    /** An early warning before a task's time ("in 15 minutes"). */
    HEADS_UP(CategorySettings()),

    /** Medication, water, meals and other regular reminders. */
    HEALTH(CategorySettings()),

    /** The summary of what was held back. */
    SUMMARY(CategorySettings(countsTowardLimit = false)),
}

/** A stretch of the day in which reminders are held back. It may run over midnight. */
data class QuietHours(
    val enabled: Boolean = false,
    val from: LocalTime = LocalTime.of(22, 0),
    val until: LocalTime = LocalTime.of(7, 0),
) {
    fun covers(time: LocalTime): Boolean = when {
        !enabled -> false
        from <= until -> time >= from && time < until
        else -> time >= from || time < until
    }
}

/**
 * Everything about reminders the user can change. Every value has a default,
 * so reminders work on a fresh install with nothing set.
 *
 * The "choices" lists are the chips offered when setting up a reminder; the
 * matching "candidates" lists below are what those chips can be picked from.
 *
 * @property dailyLimit how many reminders may interrupt in one day; zero means no limit
 * @property calmMode holds everything except kinds that break quiet hours
 * @property summaryTimes when held reminders are shown together
 * @property allDayTime when a task with a day but no time is reminded
 * @property autoRemindTimedTasks a task given a time is reminded at that time without being asked
 * @property defaultNagging the repeat used for those automatic reminders; null for none
 * @property routineWarningMinutes how long before a routine step's time is up the warning comes
 */
data class ReminderSettings(
    val categories: Map<ReminderCategory, CategorySettings> = emptyMap(),
    val quietHours: QuietHours = QuietHours(),
    val dailyLimit: Int = 30,
    val calmMode: Boolean = false,
    val summaryTimes: List<LocalTime> = listOf(LocalTime.of(9, 0), LocalTime.of(18, 0)),
    val allDayTime: LocalTime = LocalTime.of(9, 0),
    val autoRemindTimedTasks: Boolean = true,
    val defaultNagging: Nagging? = Nagging(everyMinutes = 10, times = 3),
    val leadChoices: List<Int> = listOf(0, 5, 15, 30, 60),
    val snoozeChoices: List<Int> = listOf(5, 10, 30, 60),
    val defaultSnoozeMinutes: Int = 10,
    val nagChoices: List<Int> = listOf(5, 10, 15, 30),
    val snoozeReasons: List<String> = listOf("Busy right now", "Not at home", "Too tired", "Need more time"),
    val routineWarningMinutes: Int = 2,
) {
    fun category(category: ReminderCategory): CategorySettings = categories[category] ?: category.defaults

    fun withCategory(category: ReminderCategory, change: (CategorySettings) -> CategorySettings): ReminderSettings =
        copy(categories = categories + (category to change(category(category))))

    companion object {
        /** Minutes before a task that can be offered as a "remind me" chip. 1440 is a day. */
        val LEAD_CANDIDATES: List<Int> = listOf(0, 5, 10, 15, 30, 45, 60, 120, 1440)
        val SNOOZE_CANDIDATES: List<Int> = listOf(5, 10, 15, 30, 60, 120, 180)
        val NAG_CANDIDATES: List<Int> = listOf(5, 10, 15, 30, 60)

        /** Gaps offered for an "every so often" reminder, in minutes. */
        val INTERVAL_CANDIDATES: List<Int> = listOf(30, 60, 90, 120, 180, 240)

        /** Daily limits offered as chips; zero is "no limit". */
        val DAILY_LIMIT_CHOICES: List<Int> = listOf(0, 10, 20, 30, 50, 100)

        /** Routine warnings offered as chips, in minutes; zero is "no warning". */
        val ROUTINE_WARNING_CHOICES: List<Int> = listOf(0, 1, 2, 5, 10)
        val DAILY_LIMIT_RANGE: IntRange = 0..200
        val NAG_TIMES_RANGE: IntRange = 1..20
        val ROUTINE_WARNING_RANGE: IntRange = 0..15
    }
}

interface ReminderSettingsRepository {
    val settings: Flow<ReminderSettings>

    /** Applies one change to the stored settings as a single step. */
    suspend fun update(change: (ReminderSettings) -> ReminderSettings)
}

/** What happens to a reminder that has come due. */
enum class Verdict {
    SHOW,

    /** Not shown now; listed in the next summary. */
    HOLD,

    /** Its kind is switched off: nothing happens at all. */
    DROP,
}

/**
 * The single place that decides whether a due reminder may interrupt. Every
 * notification the app sends passes through here, which is what keeps a new
 * feature from adding one that ignores quiet hours or the daily limit.
 */
object NotificationPolicy {

    /**
     * @param shownToday reminders that have already interrupted today and count toward the limit
     * @param time the clock time on the phone now
     */
    fun decide(category: CategorySettings, settings: ReminderSettings, shownToday: Int, time: LocalTime): Verdict {
        val limitReached = settings.dailyLimit > 0 && shownToday >= settings.dailyLimit
        val quietNow = settings.calmMode || settings.quietHours.covers(time)
        return when {
            !category.enabled -> Verdict.DROP
            category.delivery == Delivery.SUMMARY -> Verdict.HOLD
            quietNow && !category.breaksQuiet -> Verdict.HOLD
            limitReached && category.countsTowardLimit -> Verdict.HOLD
            else -> Verdict.SHOW
        }
    }
}
