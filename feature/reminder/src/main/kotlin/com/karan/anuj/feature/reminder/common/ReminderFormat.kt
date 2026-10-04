package com.karan.anuj.feature.reminder.common

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.karan.anuj.core.domain.reminder.Nagging
import com.karan.anuj.core.domain.reminder.ReminderCategory
import com.karan.anuj.core.domain.reminder.ReminderSchedule
import com.karan.anuj.core.domain.reminder.ReminderStyle
import com.karan.anuj.core.ui.components.clockLabel
import com.karan.anuj.feature.reminder.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * How reminder values are worded, on screen and in notifications. Kept in
 * one file so a length or a schedule reads the same wherever it appears.
 *
 * Standing conventions: lengths are "5 min", "1 h", "1 h 30 min", "2 days";
 * times are "6:30 pm" (the app-wide convention, from `clockLabel`).
 */

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

/** A length in minutes in everyday words. Takes [Resources] so a notification, which has no screen, can use it too. */
fun lengthText(resources: Resources, minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        minutes >= MINUTES_PER_DAY && minutes % MINUTES_PER_DAY == 0 ->
            resources.getQuantityString(R.plurals.reminder_days, minutes / MINUTES_PER_DAY, minutes / MINUTES_PER_DAY)
        hours == 0 -> resources.getString(R.string.reminder_minutes, rest)
        rest == 0 -> resources.getString(R.string.reminder_hours, hours)
        else -> resources.getString(R.string.reminder_hours_minutes, hours, rest)
    }
}

@Composable
fun lengthLabel(minutes: Int): String = lengthText(LocalContext.current.resources, minutes)

/** "At the time", or "15 min before". */
@Composable
fun leadLabel(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.lead_at_time) else stringResource(R.string.lead_before, lengthLabel(minutes))

/** "Off", or "Every 10 min, 3 times". */
@Composable
fun naggingLabel(nagging: Nagging?): String =
    if (nagging == null) stringResource(R.string.nag_off)
    else stringResource(R.string.nag_value, lengthLabel(nagging.everyMinutes), nagging.times)

@StringRes
fun ReminderStyle.labelRes(): Int = when (this) {
    ReminderStyle.NOTIFICATION -> R.string.style_notification
    ReminderStyle.ALARM -> R.string.style_alarm
}

@StringRes
fun ReminderCategory.labelRes(): Int = when (this) {
    ReminderCategory.TASK -> R.string.channel_task
    ReminderCategory.ALARM -> R.string.channel_alarm
    ReminderCategory.HEADS_UP -> R.string.channel_heads_up
    ReminderCategory.HEALTH -> R.string.channel_health
    ReminderCategory.SUMMARY -> R.string.channel_summary
    ReminderCategory.PLACE -> R.string.channel_place
}

fun DayOfWeek.shortLabel(): String = getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

/** "8:00 am, 8:00 pm", "Every 2 h, 9:00 am to 9:00 pm", with "· Mon, Wed" when only some days are chosen. */
@Composable
fun scheduleLabel(schedule: ReminderSchedule): String {
    val (rhythm, days) = when (schedule) {
        is ReminderSchedule.AtTimes -> schedule.times.sorted().joinToString(", ") { clockLabel(it) } to schedule.days
        is ReminderSchedule.Every -> stringResource(
            R.string.schedule_every,
            lengthLabel(schedule.everyMinutes),
            clockLabel(schedule.from),
            clockLabel(schedule.until),
        ) to schedule.days
        is ReminderSchedule.BeforeTask -> leadLabel(schedule.leadMinutes) to emptySet()
        is ReminderSchedule.Once -> stringResource(R.string.schedule_once) to emptySet()
    }
    return if (days.isEmpty()) rhythm else stringResource(R.string.schedule_on_days, rhythm, days.sorted().joinToString(", ") { it.shortLabel() })
}
