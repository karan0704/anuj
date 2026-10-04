package com.karan.anuj.feature.task.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.karan.anuj.core.domain.task.CarryOverRule
import com.karan.anuj.core.domain.task.Energy
import com.karan.anuj.core.domain.task.Priority
import com.karan.anuj.core.domain.task.Repetition
import com.karan.anuj.core.ui.components.clockLabel
import com.karan.anuj.feature.task.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * How task values are worded on screen. Kept in one file so a date, a time
 * or a repeat rule reads the same wherever it appears.
 *
 * Standing conventions: times are shown as "6:30 pm"; dates near today are
 * named ("Today", "Tomorrow"); the year is only shown when it is not this year.
 */

private val DayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val DayWithYearFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** The wording of a clock time is shared with the other features through `clockLabel` in core:ui. */
fun timeLabel(time: LocalTime): String = clockLabel(time)

@Composable
fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.task_today)
    today.plusDays(1) -> stringResource(R.string.task_tomorrow)
    today.minusDays(1) -> stringResource(R.string.task_yesterday)
    else -> date.format(if (date.year == today.year) DayFormat else DayWithYearFormat)
}

/** "Today, 6:30 pm", "Tomorrow", or null when the task has no date. */
@Composable
fun dueLabel(date: LocalDate?, time: LocalTime?, today: LocalDate): String? {
    if (date == null) return null
    val day = dateLabel(date, today)
    return if (time == null) day else stringResource(R.string.task_date_and_time, day, timeLabel(time))
}

fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

private fun Set<DayOfWeek>.names(): String = sorted().joinToString(", ") { it.shortName() }

val Weekend: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

@Composable
fun repetitionLabel(rule: Repetition?, daysOff: Set<DayOfWeek>): String {
    if (rule == null) return stringResource(R.string.repeat_never)
    /** "Every day except the weekend" has its own everyday name. */
    if (rule == Repetition.Daily(1) && daysOff == Weekend) return stringResource(R.string.repeat_weekdays)

    val rhythm = when (rule) {
        is Repetition.Daily ->
            if (rule.every <= 1) stringResource(R.string.repeat_daily)
            else stringResource(R.string.repeat_every_days, rule.every)
        is Repetition.Weekly -> {
            val base = if (rule.every <= 1) stringResource(R.string.repeat_weekly)
            else stringResource(R.string.repeat_every_weeks, rule.every)
            if (rule.days.isEmpty()) base else stringResource(R.string.repeat_on_days, base, rule.days.names())
        }
        is Repetition.Monthly ->
            if (rule.every <= 1) stringResource(R.string.repeat_monthly)
            else stringResource(R.string.repeat_every_months, rule.every)
        is Repetition.Yearly ->
            if (rule.every <= 1) stringResource(R.string.repeat_yearly)
            else stringResource(R.string.repeat_every_years, rule.every)
    }
    return if (daysOff.isEmpty()) rhythm else stringResource(R.string.repeat_except, rhythm, daysOff.names())
}

@Composable
fun carryOverLabel(rule: CarryOverRule, today: LocalDate): String = when (rule) {
    CarryOverRule.NextDay -> stringResource(R.string.carry_next_day)
    CarryOverRule.NextWeek -> stringResource(R.string.carry_next_week)
    CarryOverRule.NextMonth -> stringResource(R.string.carry_next_month)
    CarryOverRule.NextYear -> stringResource(R.string.carry_next_year)
    is CarryOverRule.InDays -> stringResource(R.string.carry_in_days, rule.days)
    is CarryOverRule.OnDate -> stringResource(R.string.carry_on_date, dateLabel(rule.date, today))
    CarryOverRule.AskMe -> stringResource(R.string.carry_ask)
    CarryOverRule.DontCarry -> stringResource(R.string.carry_dont)
}

@Composable
fun estimateLabel(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.estimate_minutes, rest)
        rest == 0 -> stringResource(R.string.estimate_hours, hours)
        else -> stringResource(R.string.estimate_hours_minutes, hours, rest)
    }
}

@Composable
fun priorityLabel(priority: Priority): String = stringResource(
    when (priority) {
        Priority.NONE -> R.string.priority_none
        Priority.LOW -> R.string.priority_low
        Priority.MEDIUM -> R.string.priority_medium
        Priority.HIGH -> R.string.priority_high
    },
)

@Composable
fun energyLabel(energy: Energy): String = stringResource(
    when (energy) {
        Energy.LOW -> R.string.energy_low
        Energy.MEDIUM -> R.string.energy_medium
        Energy.HIGH -> R.string.energy_high
    },
)

/**
 * The fixed set of tag colours. A tag stores only its position here, so a
 * colour is chosen by tapping a swatch. Mid-tone colours that stay readable
 * as a small dot on both the light and the dark background.
 */
object TagPalette {
    val colors: List<Color> = listOf(
        Color(0xFF2E9E9E),
        Color(0xFF4F7FD9),
        Color(0xFF4CAF6A),
        Color(0xFFE08A3C),
        Color(0xFFD9577A),
        Color(0xFF8B6FD6),
        Color(0xFFC9A227),
        Color(0xFF7D8A93),
    )

    /** A position outside the list (from a newer version with more colours) wraps round instead of failing. */
    fun colorOf(index: Int): Color = colors[index.mod(colors.size)]
}
