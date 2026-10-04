package com.karan.anuj.feature.reminder.platform

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.karan.anuj.core.domain.reminder.CategorySettings
import com.karan.anuj.core.domain.reminder.ReminderCategory
import com.karan.anuj.core.domain.reminder.ReminderId
import com.karan.anuj.core.domain.reminder.ReminderNotice
import com.karan.anuj.core.domain.reminder.ReminderNotifier
import com.karan.anuj.core.domain.reminder.ReminderStyle
import com.karan.anuj.feature.reminder.R
import com.karan.anuj.feature.reminder.alarm.ReminderActivity
import com.karan.anuj.feature.reminder.common.labelRes
import com.karan.anuj.feature.reminder.common.lengthText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a [ReminderNotice] into a notification.
 *
 * Android fixes a channel's sound when the channel is made, so the tone is
 * part of the channel's id: choosing another tone makes a new channel and
 * the old one is removed. Every notification carries "Done", a one-tap
 * snooze and "Later", so a reminder can be answered without opening the app.
 */
@Singleton
class AndroidReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderNotifier {

    private val manager: NotificationManagerCompat get() = NotificationManagerCompat.from(context)

    override fun show(notice: ReminderNotice) {
        val isAlarm = notice.style == ReminderStyle.ALARM
        val answerScreen = answerScreen(notice.reminderId)
        val builder = NotificationCompat.Builder(context, channelFor(notice.category, notice.settings, notice.toneUri))
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(titleOf(notice))
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(if (notice.taskId != null) openTask(notice) else answerScreen)
            .addAction(0, context.getString(R.string.action_done), action(notice, ReminderLinks.ACTION_DONE))
            .addAction(
                0,
                context.getString(R.string.action_snooze, lengthText(context.resources, notice.snoozeMinutes)),
                action(notice, ReminderLinks.ACTION_SNOOZE),
            )
            .addAction(0, context.getString(R.string.action_later), answerScreen)

        if (notice.bring.isNotEmpty()) {
            val bring = context.getString(R.string.notice_bring, notice.bring.joinToString(", "))
            builder.setContentText(bring).setStyle(NotificationCompat.BigTextStyle().bigText(bring))
        }
        if (isAlarm) {
            /** Takes over the screen when the phone is locked or idle; stays put until answered. */
            builder.setFullScreenIntent(answerScreen, true).setOngoing(true)
        }

        val notification = builder.build()
        /** An alarm keeps sounding until it is answered, instead of playing its tone once. */
        if (isAlarm) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        post(notice.reminderId.value, notification)
    }

    override fun cancel(id: ReminderId) = manager.cancel(id.value, NOTIFICATION_ID)

    override fun showSummary(titles: List<String>, settings: CategorySettings) {
        val headline = context.resources.getQuantityString(R.plurals.summary_title, titles.size, titles.size)
        val lines = NotificationCompat.InboxStyle().also { style -> titles.forEach(style::addLine) }
        val notification = NotificationCompat.Builder(context, channelFor(ReminderCategory.SUMMARY, settings, tone = null))
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(headline)
            .setContentText(titles.joinToString(", "))
            .setStyle(lines)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(context, REQUEST_OPEN_APP, ReminderLinks.openApp(context), IMMUTABLE_UPDATE),
            )
            .build()
        post(SUMMARY_TAG, notification)
    }

    /** Nothing is posted while notifications are switched off for the app; the reminder check is where that is shown. */
    @SuppressLint("MissingPermission")
    private fun post(tag: String, notification: Notification) {
        if (manager.areNotificationsEnabled()) manager.notify(tag, NOTIFICATION_ID, notification)
    }

    private fun titleOf(notice: ReminderNotice): String = when {
        notice.isRepeat -> context.getString(R.string.notice_repeat, notice.title)
        notice.leadMinutes > 0 ->
            context.getString(R.string.notice_lead, lengthText(context.resources, notice.leadMinutes), notice.title)
        else -> notice.title
    }

    /**
     * @param tone a tone for this one reminder; null uses the tone of its kind
     * @return the id of the channel to post on, made first if it does not exist yet
     */
    private fun channelFor(category: ReminderCategory, settings: CategorySettings, tone: String?): String {
        val ownTone = tone != null
        val sound = (tone ?: settings.toneUri)?.let(Uri::parse) ?: defaultSound(category)
        val prefix = category.name.lowercase() + if (ownTone) OWN_TONE_MARK else KIND_TONE_MARK
        val id = prefix + Integer.toHexString(listOf(sound.toString(), settings.vibrate).hashCode())
        if (manager.getNotificationChannel(id) != null) return id

        val kindName = context.getString(category.labelRes())
        val channel = NotificationChannel(
            id,
            if (ownTone) context.getString(R.string.channel_own_tone, kindName) else kindName,
            importanceOf(category),
        ).apply {
            setSound(sound, audioFor(category))
            enableVibration(settings.vibrate)
        }
        /** The kind's previous channel, made for its previous tone, is no longer used by anything. */
        if (!ownTone) {
            manager.notificationChannels.filter { it.id.startsWith(prefix) }.forEach { manager.deleteNotificationChannel(it.id) }
        }
        manager.createNotificationChannel(channel)
        return id
    }

    private fun defaultSound(category: ReminderCategory): Uri = RingtoneManager.getDefaultUri(
        if (category == ReminderCategory.ALARM) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION,
    )

    /** An alarm plays at the alarm volume, so it is heard with the notification volume turned down. */
    private fun audioFor(category: ReminderCategory): AudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(if (category == ReminderCategory.ALARM) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .build()

    /** An early warning and the summary arrive quietly in the shade; the rest pop up. */
    private fun importanceOf(category: ReminderCategory): Int = when (category) {
        ReminderCategory.HEADS_UP, ReminderCategory.SUMMARY, ReminderCategory.PLACE -> NotificationManager.IMPORTANCE_DEFAULT
        ReminderCategory.TASK, ReminderCategory.ALARM, ReminderCategory.HEALTH -> NotificationManager.IMPORTANCE_HIGH
    }

    /**
     * Every intent of one reminder is given that reminder's own address.
     * Android treats two pending intents that differ only in their extras
     * as the same one, which would make every "Done" finish the same task.
     */
    private fun addressOf(id: ReminderId, what: String): Uri = Uri.parse("anuj://reminder/${id.value}/$what")

    private fun action(notice: ReminderNotice, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ACTION,
        Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .setData(addressOf(notice.reminderId, action))
            .putExtra(ReminderLinks.EXTRA_REMINDER_ID, notice.reminderId.value)
            .putExtra(ReminderLinks.EXTRA_MINUTES, notice.snoozeMinutes),
        IMMUTABLE_UPDATE,
    )

    private fun answerScreen(id: ReminderId): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_ANSWER,
        ReminderActivity.intent(context, id).setData(addressOf(id, "answer")),
        IMMUTABLE_UPDATE,
    )

    private fun openTask(notice: ReminderNotice): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_TASK,
        ReminderLinks.openApp(context)
            .setData(addressOf(notice.reminderId, "open"))
            .putExtra(ReminderLinks.EXTRA_TASK_ID, notice.taskId?.value),
        IMMUTABLE_UPDATE,
    )

    private companion object {
        /** All reminders share one number and are told apart by their tag, which is the reminder's id. */
        const val NOTIFICATION_ID = 1
        const val SUMMARY_TAG = "summary"
        const val KIND_TONE_MARK = "_kind_"
        const val OWN_TONE_MARK = "_own_"
        const val REQUEST_ACTION = 10
        const val REQUEST_ANSWER = 11
        const val REQUEST_OPEN_TASK = 12
        const val REQUEST_OPEN_APP = 13
        const val IMMUTABLE_UPDATE = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
