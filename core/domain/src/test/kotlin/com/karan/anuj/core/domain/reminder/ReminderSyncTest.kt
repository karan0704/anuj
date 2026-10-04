package com.karan.anuj.core.domain.reminder

import com.karan.anuj.core.domain.task.MONDAY
import com.karan.anuj.core.domain.task.Repetition
import com.karan.anuj.core.domain.task.TaskId
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The engine as a whole: what is shown, what is logged, and when the phone is asked to wake the app next. */
class ReminderSyncTest {

    private val sixPm = LocalTime.of(18, 0)
    private val nag = Nagging(everyMinutes = 10, times = 2)

    private fun world(settings: ReminderSettings = ReminderSettings(autoRemindTimedTasks = false)) =
        ReminderWorld(settings).apply { clock.now = moment(MONDAY, 12) }

    @Test
    fun `before its time a reminder only sets the wake-up`() = runTest {
        val w = world()
        w.givenTask("Call mum", MONDAY, sixPm)
        w.givenReminder("r", task = "Call mum")

        w.syncAt(moment(MONDAY, 12, 5))

        assertTrue(w.notifier.shown.isEmpty())
        assertEquals(moment(MONDAY, 18), w.alarms.wakeAt)
        assertFalse(w.alarms.asAlarmClock)
    }

    @Test
    fun `a due reminder is shown and logged, and the next wake-up is its repeat`() = runTest {
        val w = world()
        w.givenTask("Call mum", MONDAY, sixPm)
        w.givenReminder("r", task = "Call mum", nagging = nag)

        w.syncAt(moment(MONDAY, 18))

        assertEquals(listOf("Call mum"), w.notifier.shown.map { it.title })
        assertEquals(listOf(ReminderEventKind.SHOWN), w.reminders.events.map { it.kind })
        assertEquals(moment(MONDAY, 18, 10), w.alarms.wakeAt)
    }

    @Test
    fun `an unanswered reminder repeats up to its limit and then goes quiet`() = runTest {
        val w = world()
        w.givenTask("Call mum", MONDAY, sixPm)
        w.givenReminder("r", task = "Call mum", nagging = nag)

        w.syncAt(moment(MONDAY, 18))
        w.syncAt(moment(MONDAY, 18, 10))
        w.syncAt(moment(MONDAY, 18, 20))
        w.syncAt(moment(MONDAY, 18, 30))

        assertEquals(listOf(false, true, true), w.notifier.shown.map { it.isRepeat })
        assertNull(w.alarms.wakeAt)
        assertTrue("an open task's notification stays up after the repeats end", w.notifier.cancelled.isEmpty())
    }

    @Test
    fun `running the sync twice at the same moment shows the reminder once`() = runTest {
        val w = world()
        w.givenTask("Call mum", MONDAY, sixPm)
        w.givenReminder("r", task = "Call mum")

        w.syncAt(moment(MONDAY, 18))
        w.syncAt(moment(MONDAY, 18))

        assertEquals(1, w.notifier.shown.size)
    }

    @Test
    fun `clock times missed while the phone was off arrive as one reminder`() = runTest {
        val w = world()
        w.clock.now = moment(MONDAY, 8)
        w.givenReminder(
            "Drink water",
            schedule = ReminderSchedule.Every(60, LocalTime.of(9, 0), LocalTime.of(21, 0)),
            category = ReminderCategory.HEALTH,
        )

        w.syncAt(moment(MONDAY, 12, 30))

        assertEquals(1, w.notifier.shown.size)
        assertEquals(moment(MONDAY, 13), w.alarms.wakeAt)
    }

    @Test
    fun `a task given a time is reminded at that time without being asked`() = runTest {
        val w = world(ReminderSettings())
        w.givenTask("Dentist", MONDAY, sixPm)
        w.givenTask("Some day", due = null)

        w.syncAt(moment(MONDAY, 12, 5))

        assertEquals(listOf(TaskId("Dentist")), w.reminders.all.map { it.taskId })
        assertEquals(moment(MONDAY, 18), w.alarms.wakeAt)
    }

    @Test
    fun `a task whose reminder the user removed is not given one again`() = runTest {
        val w = world(ReminderSettings())
        w.givenTask("Dentist", MONDAY, sixPm)
        w.syncAt(moment(MONDAY, 12, 5))

        w.taskReminders.setPlan(TaskId("Dentist"), TaskReminderPlan())
        w.syncAt(moment(MONDAY, 12, 6))

        assertTrue(w.reminders.getLive().isEmpty())
        assertNull(w.alarms.wakeAt)
    }

    @Test
    fun `a reminder due in quiet hours is held and arrives in the summary when they end`() = runTest {
        val quiet = QuietHours(enabled = true, from = LocalTime.of(22, 0), until = LocalTime.of(7, 0))
        val w = world(ReminderSettings(autoRemindTimedTasks = false, quietHours = quiet))
        w.givenTask("Take bins out", MONDAY, LocalTime.of(23, 0))
        w.givenReminder("r", task = "Take bins out")

        w.syncAt(moment(MONDAY, 23))

        assertTrue(w.notifier.shown.isEmpty())
        assertEquals(listOf(ReminderEventKind.HELD), w.reminders.events.map { it.kind })
        assertEquals(moment(MONDAY.plusDays(1), 7), w.alarms.wakeAt)

        w.syncAt(moment(MONDAY.plusDays(1), 7))

        assertEquals(listOf(listOf("Take bins out")), w.notifier.summaries)
        assertNull(w.alarms.wakeAt)
    }

    @Test
    fun `an alarm rings through quiet hours and asks for the alarm clock`() = runTest {
        val w = world(ReminderSettings(autoRemindTimedTasks = false, quietHours = QuietHours(enabled = true)))
        w.givenTask("Catch the train", MONDAY, LocalTime.of(23, 0))
        w.givenReminder("r", task = "Catch the train", category = ReminderCategory.ALARM, style = ReminderStyle.ALARM)

        w.syncAt(moment(MONDAY, 22, 30))
        assertTrue(w.alarms.asAlarmClock)

        w.syncAt(moment(MONDAY, 23))
        assertEquals(listOf(ReminderStyle.ALARM), w.notifier.shown.map { it.style })
    }

    @Test
    fun `past the daily limit reminders are held instead of shown`() = runTest {
        val w = world(ReminderSettings(autoRemindTimedTasks = false, dailyLimit = 1))
        w.givenTask("First", MONDAY, sixPm)
        w.givenTask("Second", MONDAY, sixPm)
        w.givenReminder("a", task = "First")
        w.givenReminder("b", task = "Second")

        w.syncAt(moment(MONDAY, 18))

        assertEquals(1, w.notifier.shown.size)
        assertEquals(setOf(ReminderEventKind.SHOWN, ReminderEventKind.HELD), w.reminders.events.map { it.kind }.toSet())
    }

    @Test
    fun `done from the reminder finishes the task and takes every notification of it down`() = runTest {
        val w = world()
        w.givenTask("Call mum", MONDAY, sixPm)
        w.givenReminder("early", task = "Call mum", schedule = ReminderSchedule.BeforeTask(15), category = ReminderCategory.HEADS_UP)
        w.givenReminder("main", task = "Call mum", nagging = nag)
        w.syncAt(moment(MONDAY, 18))

        w.clock.now = moment(MONDAY, 18, 2)
        w.answer.done(ReminderId("main"))

        assertTrue(w.world.tasks.task("Call mum").isDone)
        assertEquals(setOf(ReminderId("early"), ReminderId("main")), w.notifier.cancelled.toSet())
        assertNull(w.alarms.wakeAt)
    }

    @Test
    fun `done on a repeating task waits for its next round`() = runTest {
        val w = world()
        w.givenTask("Medicine", MONDAY, sixPm, repetition = Repetition.Daily())
        w.givenReminder("r", task = "Medicine", nagging = nag)
        w.syncAt(moment(MONDAY, 18))

        w.clock.now = moment(MONDAY, 18, 2)
        w.answer.done(ReminderId("r"))

        assertEquals(moment(MONDAY.plusDays(1), 18), w.alarms.wakeAt)
    }

    @Test
    fun `snoozing brings the reminder back after that long and keeps the reason`() = runTest {
        val w = world()
        w.givenTask("Call mum", MONDAY, sixPm)
        w.givenReminder("r", task = "Call mum", nagging = nag)
        w.syncAt(moment(MONDAY, 18))

        w.clock.now = moment(MONDAY, 18, 1)
        w.answer.snooze(ReminderId("r"), minutes = 30, reason = " Busy right now ")

        assertEquals(moment(MONDAY, 18, 31), w.alarms.wakeAt)
        val snoozed = w.reminders.events.last()
        assertEquals(Triple(ReminderEventKind.SNOOZED, 30, "Busy right now"), Triple(snoozed.kind, snoozed.minutes, snoozed.reason))

        w.syncAt(moment(MONDAY, 18, 31))
        assertEquals(2, w.notifier.shown.size)
        assertEquals("after a snooze the repeats start again", moment(MONDAY, 18, 41), w.alarms.wakeAt)
    }

    @Test
    fun `putting a task off until tomorrow moves its reminder there`() = runTest {
        val w = world()
        w.givenTask("Call mum", MONDAY, sixPm)
        w.givenReminder("r", task = "Call mum")
        w.syncAt(moment(MONDAY, 18))

        w.clock.now = moment(MONDAY, 18, 1)
        w.answer.moveToTomorrow(ReminderId("r"))

        assertEquals(MONDAY.plusDays(1), w.world.tasks.task("Call mum").dueDate)
        assertEquals(moment(MONDAY.plusDays(1), 18), w.alarms.wakeAt)
    }

    @Test
    fun `finishing the task inside the app takes its reminder down`() = runTest {
        val w = world()
        val task = w.givenTask("Call mum", MONDAY, sixPm)
        w.givenReminder("r", task = "Call mum", nagging = nag)
        w.syncAt(moment(MONDAY, 18))

        w.world.tasks.save(listOf(task.copy(completedAt = moment(MONDAY, 18, 3))))
        w.syncAt(moment(MONDAY, 18, 4))

        assertEquals(listOf(ReminderId("r")), w.notifier.cancelled)
        assertNull(w.alarms.wakeAt)
    }

    @Test
    fun `unticked checklist lines are listed as things to have ready`() = runTest {
        val w = world()
        w.givenTask("Doctor", MONDAY, sixPm)
        w.world.givenChecklistItem("Reports", task = "Doctor", checked = false)
        w.world.givenChecklistItem("Wallet", task = "Doctor", checked = true)
        w.givenReminder("r", task = "Doctor")

        w.syncAt(moment(MONDAY, 18))

        assertEquals(listOf("Reports"), w.notifier.shown.single().bring)
    }

    @Test
    fun `the nearest reminder carries the repeat and earlier ones are early warnings`() = runTest {
        val w = world()
        val plan = TaskReminderPlan(leads = setOf(0, 15), nagging = nag, style = ReminderStyle.ALARM)

        w.taskReminders.setPlan(TaskId("Call mum"), plan)

        val rows = w.reminders.all.associateBy { (it.schedule as ReminderSchedule.BeforeTask).leadMinutes }
        assertEquals(ReminderCategory.ALARM to nag, rows.getValue(0).category to rows.getValue(0).nagging)
        assertEquals(ReminderCategory.HEADS_UP to null, rows.getValue(15).category to rows.getValue(15).nagging)
        assertEquals(plan, w.taskReminders.observePlan(TaskId("Call mum")).first())
    }

    @Test
    fun `editing a regular reminder does not set off a time that has already passed today`() = runTest {
        val w = world()
        w.clock.now = moment(MONDAY, 6)
        val added = w.standing.add(StandingPresets.medication)

        w.clock.now = moment(MONDAY, 12)
        w.standing.update(added.id) { it.copy(schedule = ReminderSchedule.AtTimes(listOf(LocalTime.of(7, 0)))) }
        w.syncAt(moment(MONDAY, 12, 1))

        assertTrue(w.notifier.shown.isEmpty())
        assertEquals(moment(MONDAY.plusDays(1), 7), w.alarms.wakeAt)
    }
}
