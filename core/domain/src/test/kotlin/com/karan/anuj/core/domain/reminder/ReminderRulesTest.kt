package com.karan.anuj.core.domain.reminder

import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.task.MONDAY
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a reminder is due, whether it may interrupt, and when it repeats. */
class ReminderRulesTest {

    private val nineOClock = LocalTime.of(9, 0)

    private fun context(task: Task? = null, zone: ZoneId = INDIA) = ScheduleContext(zone, task, allDayTime = nineOClock)

    private fun task(due: LocalDate?, time: LocalTime? = null, completedAt: Long? = null) = Task(
        id = TaskId("t"),
        name = "Call mum",
        dueDate = due,
        dueTime = time,
        completedAt = completedAt,
        stamps = RecordStamps.created(0),
    )

    private fun reminder(
        schedule: ReminderSchedule = ReminderSchedule.BeforeTask(),
        nagging: Nagging? = null,
        state: ReminderState = ReminderState(),
        task: Boolean = true,
        enabled: Boolean = true,
    ) = Reminder(
        id = ReminderId("r"),
        taskId = TaskId("t").takeIf { task },
        schedule = schedule,
        nagging = nagging,
        enabled = enabled,
        state = state,
        stamps = RecordStamps.created(0),
    )

    // ---- schedules

    @Test
    fun `a task reminder is due its lead before the task's time`() {
        val at = ReminderSchedule.BeforeTask(leadMinutes = 15)
            .nextOccurrence(after = 0, context(task(MONDAY, LocalTime.of(18, 0))))

        assertEquals(moment(MONDAY, 17, 45), at)
    }

    @Test
    fun `a task with a day but no time is reminded at the all-day time`() {
        val at = ReminderSchedule.BeforeTask().nextOccurrence(after = 0, context(task(MONDAY)))

        assertEquals(moment(MONDAY, 9), at)
    }

    @Test
    fun `a task with no day has nothing to be reminded of`() {
        assertNull(ReminderSchedule.BeforeTask().nextOccurrence(after = 0, context(task(due = null))))
    }

    @Test
    fun `a task time already dealt with is not due again`() {
        val schedule = ReminderSchedule.BeforeTask()
        val planned = moment(MONDAY, 18)

        assertNull(schedule.nextOccurrence(after = planned, context(task(MONDAY, LocalTime.of(18, 0)))))
    }

    @Test
    fun `clock times give the next one today and then tomorrow's first`() {
        val schedule = ReminderSchedule.AtTimes(listOf(LocalTime.of(20, 0), LocalTime.of(8, 0)))

        assertEquals(moment(MONDAY, 20), schedule.nextOccurrence(after = moment(MONDAY, 8), context()))
        assertEquals(moment(MONDAY.plusDays(1), 8), schedule.nextOccurrence(after = moment(MONDAY, 20), context()))
    }

    @Test
    fun `clock times skip the days that are not chosen`() {
        val schedule = ReminderSchedule.AtTimes(listOf(nineOClock), days = setOf(DayOfWeek.THURSDAY))

        assertEquals(moment(MONDAY.plusDays(3), 9), schedule.nextOccurrence(after = moment(MONDAY, 10), context()))
    }

    @Test
    fun `every two hours from nine to nine is seven times a day`() {
        val schedule = ReminderSchedule.Every(everyMinutes = 120, from = nineOClock, until = LocalTime.of(21, 0))

        assertEquals(listOf(9, 11, 13, 15, 17, 19, 21), schedule.timesOfDay().map { it.hour })
        assertEquals(moment(MONDAY, 13), schedule.nextOccurrence(after = moment(MONDAY, 11, 30), context()))
    }

    @Test
    fun `an interval too short to be useful is widened instead of buzzing constantly`() {
        val schedule = ReminderSchedule.Every(everyMinutes = 0, from = nineOClock, until = LocalTime.of(9, 10))

        assertEquals(listOf(0, 5, 10), schedule.timesOfDay().map { it.minute })
    }

    @Test
    fun `a clock time stays the same clock time across a daylight-saving change`() {
        /** Berlin's clocks go back an hour on 25 October 2026, making that day 25 hours long. */
        val berlin = ZoneId.of("Europe/Berlin")
        val saturday = LocalDate.of(2026, 10, 24)
        val schedule = ReminderSchedule.AtTimes(listOf(LocalTime.of(8, 0)))

        val next = schedule.nextOccurrence(after = moment(saturday, 8, zone = berlin), context(zone = berlin))

        assertEquals(moment(saturday.plusDays(1), 8, zone = berlin), next)
        assertEquals(25 * 60 * ReminderPlanner.MILLIS_PER_MINUTE, next!! - moment(saturday, 8, zone = berlin))
    }

    @Test
    fun `every kind of schedule reads back as it was stored`() {
        val schedules = listOf(
            ReminderSchedule.BeforeTask(30),
            ReminderSchedule.AtTimes(listOf(LocalTime.of(8, 30), LocalTime.of(20, 0)), setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)),
            ReminderSchedule.Every(90, nineOClock, LocalTime.of(21, 0), setOf(DayOfWeek.FRIDAY)),
            ReminderSchedule.Once(123_456L),
        )

        schedules.forEach { assertEquals(it, ReminderScheduleCodec.decode(ReminderScheduleCodec.encode(it))) }
    }

    @Test
    fun `stored text this version cannot read gives no schedule instead of failing`() {
        listOf(null, "", "NEWKIND;1", "TIMES;;0", "EVERY;x;1;2;0", "ONCE;soon").forEach {
            assertNull("\"$it\" must not decode", ReminderScheduleCodec.decode(it))
        }
    }

    // ---- may it interrupt

    private val normal = CategorySettings()

    private fun verdict(
        category: CategorySettings = normal,
        settings: ReminderSettings = ReminderSettings(),
        shownToday: Int = 0,
        time: LocalTime = LocalTime.NOON,
    ) = NotificationPolicy.decide(category, settings, shownToday, time)

    @Test
    fun `quiet hours that run over midnight cover late evening and early morning only`() {
        val quiet = QuietHours(enabled = true, from = LocalTime.of(22, 0), until = LocalTime.of(7, 0))

        assertTrue(quiet.covers(LocalTime.of(23, 30)))
        assertTrue(quiet.covers(LocalTime.of(6, 59)))
        assertFalse(quiet.covers(LocalTime.of(7, 0)))
        assertFalse(quiet.covers(LocalTime.NOON))
        assertFalse(quiet.copy(enabled = false).covers(LocalTime.of(23, 30)))
    }

    @Test
    fun `a reminder in quiet hours is held for the summary`() {
        val settings = ReminderSettings(quietHours = QuietHours(enabled = true))

        assertEquals(Verdict.HOLD, verdict(settings = settings, time = LocalTime.of(23, 0)))
        assertEquals(Verdict.SHOW, verdict(settings = settings, time = LocalTime.NOON))
    }

    @Test
    fun `calm mode holds everything that does not break quiet hours`() {
        val calm = ReminderSettings(calmMode = true)

        assertEquals(Verdict.HOLD, verdict(settings = calm))
        assertEquals(Verdict.SHOW, verdict(ReminderCategory.ALARM.defaults, calm))
    }

    @Test
    fun `past the daily limit a reminder is held, but an alarm still rings`() {
        val settings = ReminderSettings(dailyLimit = 5)

        assertEquals(Verdict.SHOW, verdict(settings = settings, shownToday = 4))
        assertEquals(Verdict.HOLD, verdict(settings = settings, shownToday = 5))
        assertEquals(Verdict.SHOW, verdict(ReminderCategory.ALARM.defaults, settings, shownToday = 50))
    }

    @Test
    fun `a daily limit of zero means no limit`() {
        assertEquals(Verdict.SHOW, verdict(settings = ReminderSettings(dailyLimit = 0), shownToday = 10_000))
    }

    @Test
    fun `a kind moved to the summary never interrupts, and a kind switched off does nothing`() {
        assertEquals(Verdict.HOLD, verdict(normal.copy(delivery = Delivery.SUMMARY)))
        assertEquals(Verdict.DROP, verdict(normal.copy(enabled = false)))
    }

    // ---- when is it next due

    private val sixPm = task(MONDAY, LocalTime.of(18, 0))
    private val firedAtSix = ReminderState(lastOccurrenceAt = moment(MONDAY, 18), lastFiredAt = moment(MONDAY, 18))

    @Test
    fun `a reminder nobody answered repeats after its gap`() {
        val next = ReminderPlanner.next(reminder(nagging = Nagging(10, times = 3), state = firedAtSix), context(sixPm))

        assertEquals(PlannedFire.Nag(moment(MONDAY, 18, 10)), next)
    }

    @Test
    fun `the repeats stop once their limit is reached`() {
        val worn = firedAtSix.copy(nagsSent = 3)

        assertNull(ReminderPlanner.next(reminder(nagging = Nagging(10, times = 3), state = worn), context(sixPm)))
    }

    @Test
    fun `an answered reminder does not repeat`() {
        val answered = firedAtSix.copy(answeredAt = moment(MONDAY, 18, 1))

        assertNull(ReminderPlanner.next(reminder(nagging = Nagging(10, times = 3), state = answered), context(sixPm)))
    }

    @Test
    fun `a snooze is the only thing due until it is over`() {
        val snoozed = firedAtSix.copy(snoozedUntil = moment(MONDAY, 18, 30), answeredAt = moment(MONDAY, 18, 1))

        assertEquals(
            PlannedFire.SnoozeOver(moment(MONDAY, 18, 30)),
            ReminderPlanner.next(reminder(nagging = Nagging(10, times = 3), state = snoozed), context(sixPm)),
        )
    }

    @Test
    fun `a finished task, a switched-off reminder and a missing task are never due`() {
        assertNull(ReminderPlanner.next(reminder(), context(sixPm.copy(completedAt = 5))))
        assertNull(ReminderPlanner.next(reminder(enabled = false), context(sixPm)))
        assertNull(ReminderPlanner.next(reminder(), context(task = null)))
    }

    @Test
    fun `a task moved to a later day is reminded then, and the old showing is not repeated`() {
        val moved = sixPm.copy(dueDate = MONDAY.plusDays(1))

        val next = ReminderPlanner.next(reminder(nagging = Nagging(10, times = 3), state = firedAtSix), context(moved))

        assertEquals(PlannedFire.Occurrence(moment(MONDAY.plusDays(1), 18)), next)
    }

    @Test
    fun `a standing reminder repeats until its next clock time takes over`() {
        val schedule = ReminderSchedule.AtTimes(listOf(LocalTime.of(8, 0), LocalTime.of(8, 15)))
        val firedAtEight = ReminderState(lastOccurrenceAt = moment(MONDAY, 8), lastFiredAt = moment(MONDAY, 8))
        val standing = reminder(schedule, Nagging(10, times = 3), firedAtEight, task = false)

        assertEquals(PlannedFire.Nag(moment(MONDAY, 8, 10)), ReminderPlanner.next(standing, context()))

        val naggedOnce = standing.copy(state = firedAtEight.copy(lastFiredAt = moment(MONDAY, 8, 10), nagsSent = 1))
        assertEquals(PlannedFire.Occurrence(moment(MONDAY, 8, 15)), ReminderPlanner.next(naggedOnce, context()))
    }
}
