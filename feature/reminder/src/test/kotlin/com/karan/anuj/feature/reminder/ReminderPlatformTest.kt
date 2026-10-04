package com.karan.anuj.feature.reminder

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import androidx.test.core.app.ApplicationProvider
import com.karan.anuj.core.domain.reminder.CategorySettings
import com.karan.anuj.core.domain.reminder.ReminderCategory
import com.karan.anuj.core.domain.reminder.ReminderId
import com.karan.anuj.core.domain.reminder.ReminderNotice
import com.karan.anuj.core.domain.reminder.ReminderStyle
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.feature.reminder.platform.AndroidAlarmGateway
import com.karan.anuj.feature.reminder.platform.AndroidReminderNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The two places the reminder rules touch the phone, checked against the
 * phone's own notification and alarm services as Robolectric models them.
 * Whether a real phone then lets the alarm through is what the in-app
 * reminder check is for; that cannot be shown here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderPlatformTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarmService = context.getSystemService(AlarmManager::class.java)
    private val notifier = AndroidReminderNotifier(context)
    private val alarms = AndroidAlarmGateway(context)

    private fun notice(
        id: String = "r",
        category: ReminderCategory = ReminderCategory.TASK,
        settings: CategorySettings = category.defaults,
        style: ReminderStyle = ReminderStyle.NOTIFICATION,
        tone: String? = null,
        lead: Int = 0,
        repeat: Boolean = false,
        bring: List<String> = emptyList(),
    ) = ReminderNotice(
        reminderId = ReminderId(id),
        taskId = TaskId("task"),
        title = "Call mum",
        category = category,
        settings = settings,
        style = style,
        toneUri = tone,
        leadMinutes = lead,
        isRepeat = repeat,
        bring = bring,
        snoozeMinutes = 10,
    )

    private fun posted(): List<Notification> = notifications.activeNotifications.map { it.notification }

    private fun titleOf(notification: Notification) = notification.extras.getString(Notification.EXTRA_TITLE)

    @Test
    fun `a reminder can be answered from the notification itself`() {
        notifier.show(notice())

        val shown = posted().single()
        assertEquals("Call mum", titleOf(shown))
        assertEquals(listOf("Done", "Snooze 10 min", "Later"), shown.actions.map { it.title.toString() })
    }

    @Test
    fun `an early warning says how long is left, and a repeat says it is still to do`() {
        notifier.show(notice(id = "early", category = ReminderCategory.HEADS_UP, lead = 90))
        notifier.show(notice(id = "again", repeat = true))

        assertEquals(setOf("In 1 h 30 min: Call mum", "Still to do: Call mum"), posted().map(::titleOf).toSet())
    }

    @Test
    fun `things to have ready are listed under the title`() {
        notifier.show(notice(bring = listOf("Reports", "Wallet")))

        assertEquals("Have ready: Reports, Wallet", posted().single().extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `an alarm takes over the screen, keeps sounding and plays at the alarm volume`() {
        notifier.show(notice(category = ReminderCategory.ALARM, style = ReminderStyle.ALARM))

        val shown = posted().single()
        assertNotNull(shown.fullScreenIntent)
        assertTrue(shown.flags and Notification.FLAG_INSISTENT != 0)
        val channel = notifications.getNotificationChannel(shown.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(AudioAttributes.USAGE_ALARM, channel.audioAttributes.usage)
    }

    @Test
    fun `cancelling takes down that reminder and leaves the others`() {
        notifier.show(notice(id = "a"))
        notifier.show(notice(id = "b"))

        notifier.cancel(ReminderId("a"))

        assertEquals(listOf("b"), notifications.activeNotifications.map { it.tag })
    }

    @Test
    fun `changing the tone of a kind replaces its channel instead of leaving two behind`() {
        notifier.show(notice(settings = CategorySettings(toneUri = "content://tones/1")))
        notifier.show(notice(settings = CategorySettings(toneUri = "content://tones/2")))

        val channels = notifications.notificationChannels.filter { it.id.startsWith("task_") }
        assertEquals(listOf("content://tones/2"), channels.map { it.sound.toString() })
    }

    @Test
    fun `a reminder with its own tone gets its own channel beside the one of its kind`() {
        notifier.show(notice(id = "plain"))
        notifier.show(notice(id = "special", tone = "content://tones/9"))

        val names = notifications.notificationChannels.map { it.name.toString() }.toSet()
        assertEquals(setOf("Task reminders", "Task reminders (own tone)"), names)
    }

    @Test
    fun `nothing is posted while notifications are switched off for the app`() {
        shadowOf(notifications).setNotificationsEnabled(false)

        notifier.show(notice())

        assertTrue(posted().isEmpty())
    }

    @Test
    fun `the summary names what was held back`() {
        notifier.showSummary(listOf("Take bins out", "Call mum"), ReminderCategory.SUMMARY.defaults)

        val shown = posted().single()
        assertEquals("2 reminders were held back", titleOf(shown))
        assertEquals("Take bins out, Call mum", shown.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `only one wake-up is held, and the latest replaces the one before`() {
        alarms.setNext(1_000_000, asAlarmClock = false)
        alarms.setNext(2_000_000, asAlarmClock = false)

        val scheduled = shadowOf(alarmService).scheduledAlarms
        assertEquals(listOf(2_000_000L), scheduled.map { it.triggerAtMs })
    }

    @Test
    fun `no moment to wake at cancels the wake-up`() {
        alarms.setNext(1_000_000, asAlarmClock = false)

        alarms.setNext(null, asAlarmClock = false)

        assertTrue(shadowOf(alarmService).scheduledAlarms.isEmpty())
    }

    @Test
    fun `a full-screen alarm is set as an alarm clock when exact alarms are allowed`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        alarms.setNext(1_000_000, asAlarmClock = true)

        assertNotNull(shadowOf(alarmService).scheduledAlarms.single().alarmClockInfo)
    }

    @Test
    fun `without the exact-alarm permission the wake-up is still set, as an ordinary one`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        alarms.setNext(1_000_000, asAlarmClock = true)

        val scheduled = shadowOf(alarmService).scheduledAlarms.single()
        assertEquals(1_000_000L, scheduled.triggerAtMs)
        assertNull(scheduled.alarmClockInfo)
    }
}
