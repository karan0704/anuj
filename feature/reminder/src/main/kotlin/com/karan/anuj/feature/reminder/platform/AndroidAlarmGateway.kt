package com.karan.anuj.feature.reminder.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.karan.anuj.core.domain.reminder.AlarmGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The names that travel inside reminder intents, shared by everything that sends or reads one. */
object ReminderLinks {
    /** On the intent that opens the app from a reminder: the task to show. */
    const val EXTRA_TASK_ID = "com.karan.anuj.reminder.TASK_ID"
    const val EXTRA_REMINDER_ID = "com.karan.anuj.reminder.REMINDER_ID"
    const val EXTRA_MINUTES = "com.karan.anuj.reminder.MINUTES"
    const val ACTION_DONE = "com.karan.anuj.reminder.DONE"
    const val ACTION_SNOOZE = "com.karan.anuj.reminder.SNOOZE"

    /**
     * The intent that opens the app's main screen. It names that screen
     * directly instead of reusing the launcher's own intent: Android answers
     * a second launcher intent by only bringing the app forward, and the
     * task to open, which travels in the extras, would be dropped.
     */
    fun openApp(context: Context): Intent =
        Intent(Intent.ACTION_VIEW)
            .setPackage(context.packageName)
            .setComponent(context.packageManager.getLaunchIntentForPackage(context.packageName)?.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

/**
 * One wake-up is held with the phone at a time: the moment the next
 * reminder is due. When it goes off, the engine shows what is due and sets
 * the next one, so there is never a pile of alarms to keep in step.
 */
@Singleton
class AndroidAlarmGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmGateway {

    private val manager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    override fun setNext(atMillis: Long?, asAlarmClock: Boolean) {
        val wakeUp = PendingIntent.getBroadcast(
            context,
            REQUEST_WAKE_UP,
            Intent(context, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.cancel(wakeUp)
        if (atMillis == null) return

        try {
            when {
                !canBeExact() -> setInexact(atMillis, wakeUp)

                /** An alarm clock is the one kind of alarm the phone never delays, and it shows in the status bar. */
                asAlarmClock -> manager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, showApp()), wakeUp)

                else -> manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, wakeUp)
            }
        } catch (permissionTakenAway: SecurityException) {
            /** The permission can be withdrawn between the check and the call. */
            setInexact(atMillis, wakeUp)
        }
    }

    /**
     * Without the exact-alarm permission the phone may deliver this some
     * minutes late. Late is still better than never; the reminder check
     * tells the user how to allow exact alarms.
     */
    private fun setInexact(atMillis: Long, wakeUp: PendingIntent) =
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, wakeUp)

    fun canBeExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun showApp(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_SHOW_APP,
        ReminderLinks.openApp(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_WAKE_UP = 1
        const val REQUEST_SHOW_APP = 2
    }
}
