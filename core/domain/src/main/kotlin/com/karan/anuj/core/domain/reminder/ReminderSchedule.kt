package com.karan.anuj.core.domain.reminder

import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.WeekdaysCodec
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * What a schedule needs to know about the world to work out its times.
 *
 * @property task the task the reminder belongs to, when it belongs to one
 * @property allDayTime the clock time used for a task that has a day but no time
 */
data class ScheduleContext(
    val zone: ZoneId,
    val task: Task? = null,
    val allDayTime: LocalTime,
)

/**
 * When a reminder is due. Each kind works out its own times, so a new kind
 * (a place, a phone-use limit) is a new implementation and nothing that uses
 * schedules has to change.
 *
 * Clock times are calendar values: "8:00" is turned into an instant in the
 * phone's zone on each day, so it stays 8:00 through a time-zone change or a
 * daylight-saving switch.
 */
sealed interface ReminderSchedule {

    /** The first planned moment strictly after [after], or null when there is none. */
    fun nextOccurrence(after: Long, context: ScheduleContext): Long?

    /** [leadMinutes] before the task's time, on the task's day. Zero is "at the time". */
    data class BeforeTask(val leadMinutes: Int = 0) : ReminderSchedule {
        override fun nextOccurrence(after: Long, context: ScheduleContext): Long? {
            val task = context.task ?: return null
            val date = task.dueDate ?: return null
            val time = task.dueTime ?: context.allDayTime
            val at = date.atTime(time).minusMinutes(leadMinutes.toLong()).atZone(context.zone).toInstant().toEpochMilli()
            return at.takeIf { it > after }
        }
    }

    /** At fixed clock times. An empty [days] means every day. */
    data class AtTimes(val times: List<LocalTime>, val days: Set<DayOfWeek> = emptySet()) : ReminderSchedule {
        override fun nextOccurrence(after: Long, context: ScheduleContext): Long? =
            firstClockTimeAfter(after, context.zone, days, times)
    }

    /** Every [everyMinutes] from [from] to [until], both included. An empty [days] means every day. */
    data class Every(
        val everyMinutes: Int,
        val from: LocalTime,
        val until: LocalTime,
        val days: Set<DayOfWeek> = emptySet(),
    ) : ReminderSchedule {

        /** The clock times of one day. A window that ends before it starts gives just its start. */
        fun timesOfDay(): List<LocalTime> {
            val start = from.toMinuteOfDay()
            val end = until.toMinuteOfDay().coerceAtLeast(start)
            return (start..end step everyMinutes.coerceAtLeast(SHORTEST_GAP_MINUTES)).map { LocalTime.of(it / 60, it % 60) }
        }

        override fun nextOccurrence(after: Long, context: ScheduleContext): Long? =
            firstClockTimeAfter(after, context.zone, days, timesOfDay())
    }

    /** Once, at an exact moment. Used for "remind me in an hour" and for the reminder test. */
    data class Once(val at: Long) : ReminderSchedule {
        override fun nextOccurrence(after: Long, context: ScheduleContext): Long? = at.takeIf { it > after }
    }

    companion object {
        /** A gap shorter than this would turn a reminder into a constant buzz. */
        const val SHORTEST_GAP_MINUTES = 5
    }
}

private fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

/**
 * Walks forward a day at a time from [after] and returns the first of
 * [times] that falls later. A week and a day is enough to reach any weekday.
 */
private fun firstClockTimeAfter(after: Long, zone: ZoneId, days: Set<DayOfWeek>, times: List<LocalTime>): Long? {
    if (times.isEmpty()) return null
    val ordered = times.sorted()
    var date = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
    repeat(DAYS_TO_SEARCH) {
        if (days.isEmpty() || date.dayOfWeek in days) {
            ordered.forEach { time ->
                val at = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                if (at > after) return at
            }
        }
        date = date.plusDays(1)
    }
    return null
}

private const val DAYS_TO_SEARCH = 8

/**
 * The text form of a schedule, for storage and backup files. Decoding never
 * throws: text this version does not understand reads back as null.
 */
object ReminderScheduleCodec {

    fun encode(schedule: ReminderSchedule): String = when (schedule) {
        is ReminderSchedule.BeforeTask -> "TASK;${schedule.leadMinutes}"
        is ReminderSchedule.AtTimes ->
            "TIMES;${schedule.times.joinToString(",") { it.toMinuteOfDay().toString() }};${WeekdaysCodec.encode(schedule.days)}"
        is ReminderSchedule.Every ->
            "EVERY;${schedule.everyMinutes};${schedule.from.toMinuteOfDay()};${schedule.until.toMinuteOfDay()};" +
                WeekdaysCodec.encode(schedule.days)
        is ReminderSchedule.Once -> "ONCE;${schedule.at}"
    }

    fun decode(text: String?): ReminderSchedule? {
        val parts = text?.split(";") ?: return null
        return when (parts[0]) {
            "TASK" -> parts.intAt(1)?.let { ReminderSchedule.BeforeTask(it) }
            "TIMES" -> {
                val times = parts.getOrNull(1).orEmpty().split(",").mapNotNull { it.toIntOrNull()?.toClockTime() }
                if (times.isEmpty()) null else ReminderSchedule.AtTimes(times, parts.daysAt(2))
            }
            "EVERY" -> {
                val every = parts.intAt(1) ?: return null
                val from = parts.intAt(2)?.toClockTime() ?: return null
                val until = parts.intAt(3)?.toClockTime() ?: return null
                ReminderSchedule.Every(every, from, until, parts.daysAt(4))
            }
            "ONCE" -> parts.getOrNull(1)?.toLongOrNull()?.let { ReminderSchedule.Once(it) }
            else -> null
        }
    }

    private fun List<String>.intAt(index: Int): Int? = getOrNull(index)?.toIntOrNull()

    private fun List<String>.daysAt(index: Int): Set<DayOfWeek> = WeekdaysCodec.decode(intAt(index) ?: 0)

    private fun Int.toClockTime(): LocalTime? =
        if (this in 0 until MINUTES_PER_DAY) LocalTime.of(this / 60, this % 60) else null

    private const val MINUTES_PER_DAY = 24 * 60
}
