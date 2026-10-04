package com.karan.anuj.feature.task.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.karan.anuj.core.domain.task.CarryOverRule
import com.karan.anuj.core.domain.task.DayParts
import com.karan.anuj.core.domain.task.Repetition
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.MinTouchTarget
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.Stepper
import com.karan.anuj.feature.task.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * The sheets and dialogs a task's values are chosen with. Each offers the
 * common answers as chips first and a full picker behind one more tap, so
 * the usual case never needs the keyboard.
 */

/**
 * The clock times behind the part-of-day chips are the user's own, passed in
 * as [DayParts] and changed in Settings. They used to be fixed here:
 *
 *     private val Morning: LocalTime = LocalTime.of(8, 0)
 *     private val Afternoon: LocalTime = LocalTime.of(13, 0)
 *     private val Evening: LocalTime = LocalTime.of(18, 0)
 *     private val Night: LocalTime = LocalTime.of(21, 0)
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    /** The picker works in UTC midnights, so the date goes in and comes out through UTC and never shifts by a day. */
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.task_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.task_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: LocalTime, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onPicked(LocalTime.of(state.hour, state.minute))
                    onDismiss()
                },
            ) { Text(stringResource(R.string.task_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.task_cancel)) } },
        text = { TimePicker(state = state) },
    )
}

/**
 * Chips for the day: the near days by name, "No date" where allowed, and a
 * calendar behind "Pick a date". A date that is none of the named ones gets
 * its own selected chip so the current choice is always visible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateChips(
    date: LocalDate?,
    today: LocalDate,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    allowNoDate: Boolean = true,
) {
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val named = listOf(
        today to stringResource(R.string.task_today),
        today.plusDays(1) to stringResource(R.string.task_tomorrow),
        today.plusWeeks(1) to stringResource(R.string.task_next_week),
        today.plusMonths(1) to stringResource(R.string.task_next_month),
    )

    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        named.forEach { (day, label) ->
            FilterChip(selected = date == day, onClick = { onChange(day) }, label = { Text(label) })
        }
        if (date != null && named.none { it.first == date }) {
            FilterChip(selected = true, onClick = { showCalendar = true }, label = { Text(dateLabel(date, today)) })
        }
        if (allowNoDate) {
            FilterChip(selected = date == null, onClick = { onChange(null) }, label = { Text(stringResource(R.string.task_no_date)) })
        }
        AssistChip(onClick = { showCalendar = true }, label = { Text(stringResource(R.string.task_pick_date)) })
    }

    if (showCalendar) {
        DatePickDialog(initial = date ?: today, onPicked = onChange, onDismiss = { showCalendar = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeChips(
    time: LocalTime?,
    onChange: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
    parts: DayParts = DayParts(),
) {
    var showClock by rememberSaveable { mutableStateOf(false) }
    val named = listOf(
        parts.morning to stringResource(R.string.task_morning),
        parts.afternoon to stringResource(R.string.task_afternoon),
        parts.evening to stringResource(R.string.task_evening),
        parts.night to stringResource(R.string.task_night),
    )

    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        named.forEach { (clock, label) ->
            FilterChip(selected = time == clock, onClick = { onChange(clock) }, label = { Text(label) })
        }
        if (time != null && named.none { it.first == time }) {
            FilterChip(selected = true, onClick = { showClock = true }, label = { Text(timeLabel(time)) })
        }
        FilterChip(selected = time == null, onClick = { onChange(null) }, label = { Text(stringResource(R.string.task_no_time)) })
        AssistChip(onClick = { showClock = true }, label = { Text(stringResource(R.string.task_pick_time)) })
    }

    if (showClock) {
        TimePickDialog(initial = time ?: parts.morning, onPicked = onChange, onDismiss = { showClock = false })
    }
}

@Composable
fun DueSheet(
    date: LocalDate?,
    time: LocalTime?,
    today: LocalDate,
    allowNoDate: Boolean,
    onDateChange: (LocalDate?) -> Unit,
    onTimeChange: (LocalTime?) -> Unit,
    onDismiss: () -> Unit,
    dayParts: DayParts = DayParts(),
) {
    AnujBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.detail_when)) {
        DateChips(date, today, onDateChange, allowNoDate = allowNoDate)
        Spacer(Modifier.height(16.dp))
        TimeChips(time, onTimeChange, parts = dayParts)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.task_ok), onClick = onDismiss)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekdayChips(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(selected = day in selected, onClick = { onToggle(day) }, label = { Text(day.shortName()) })
        }
    }
}

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

/** The named repeat choices offered as chips. */
private enum class RepeatPreset { NEVER, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY }

private fun presetOf(rule: Repetition?, daysOff: Set<DayOfWeek>): RepeatPreset = when (rule) {
    null -> RepeatPreset.NEVER
    is Repetition.Daily -> if (rule.every <= 1 && daysOff == Weekend) RepeatPreset.WEEKDAYS else RepeatPreset.DAILY
    is Repetition.Weekly -> RepeatPreset.WEEKLY
    is Repetition.Monthly -> RepeatPreset.MONTHLY
    is Repetition.Yearly -> RepeatPreset.YEARLY
}

/**
 * @param anchor the day the task is due on; a weekly, monthly or yearly
 * rhythm starts from this day's weekday or date
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatSheet(
    rule: Repetition?,
    daysOff: Set<DayOfWeek>,
    anchor: LocalDate,
    onChange: (Repetition?, Set<DayOfWeek>) -> Unit,
    onDismiss: () -> Unit,
) {
    val preset = presetOf(rule, daysOff)

    AnujBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.detail_repeat)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepeatPreset.entries.forEach { option ->
                    FilterChip(
                        selected = option == preset,
                        onClick = {
                            when (option) {
                                RepeatPreset.NEVER -> onChange(null, daysOff)
                                /** Leaving "Weekdays" for "Every day" brings the weekend back. */
                                RepeatPreset.DAILY -> onChange(Repetition.Daily(), if (preset == RepeatPreset.WEEKDAYS) emptySet() else daysOff)
                                RepeatPreset.WEEKDAYS -> onChange(Repetition.Daily(), Weekend)
                                RepeatPreset.WEEKLY -> onChange(Repetition.Weekly(days = setOf(anchor.dayOfWeek)), daysOff)
                                RepeatPreset.MONTHLY -> onChange(Repetition.Monthly(dayOfMonth = anchor.dayOfMonth), daysOff)
                                RepeatPreset.YEARLY -> onChange(Repetition.Yearly(month = anchor.monthValue, dayOfMonth = anchor.dayOfMonth), daysOff)
                            }
                        },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        RepeatPreset.NEVER -> R.string.repeat_never
                                        RepeatPreset.DAILY -> R.string.repeat_daily
                                        RepeatPreset.WEEKDAYS -> R.string.repeat_weekdays
                                        RepeatPreset.WEEKLY -> R.string.repeat_weekly
                                        RepeatPreset.MONTHLY -> R.string.repeat_monthly
                                        RepeatPreset.YEARLY -> R.string.repeat_yearly
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            if (rule != null) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.repeat_gap), style = MaterialTheme.typography.titleSmall)
                    Stepper(
                        value = rule.every.coerceAtLeast(1),
                        onChange = { every ->
                            onChange(
                                when (rule) {
                                    is Repetition.Daily -> rule.copy(every = every)
                                    is Repetition.Weekly -> rule.copy(every = every)
                                    is Repetition.Monthly -> rule.copy(every = every)
                                    is Repetition.Yearly -> rule.copy(every = every)
                                },
                                daysOff,
                            )
                        },
                        range = 1..99,
                        lessLabel = stringResource(R.string.repeat_gap_less),
                        moreLabel = stringResource(R.string.repeat_gap_more),
                    )
                }
                Text(
                    repetitionLabel(rule, daysOff),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                if (rule is Repetition.Weekly) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.repeat_on), style = MaterialTheme.typography.titleSmall)
                    WeekdayChips(rule.days, onToggle = { onChange(rule.copy(days = rule.days.toggled(it)), daysOff) })
                }

                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.repeat_days_off), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.repeat_days_off_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WeekdayChips(daysOff, onToggle = { onChange(rule, daysOff.toggled(it)) })
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = stringResource(R.string.task_ok), onClick = onDismiss)
        }
    }
}

/** A list where exactly one line is chosen, each line a full-width target. */
@Composable
private fun ChoiceLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * @param inheritLabel the wording of the "no rule of its own" choice, or
 * null to leave that choice out (the app-wide default must always be a rule)
 */
@Composable
fun CarryOverSheet(
    title: String,
    rule: CarryOverRule?,
    inheritLabel: String?,
    today: LocalDate,
    onChange: (CarryOverRule?) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCalendar by rememberSaveable { mutableStateOf(false) }

    AnujBottomSheet(onDismiss = onDismiss, title = title) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (inheritLabel != null) {
                ChoiceLine(inheritLabel, selected = rule == null, onClick = { onChange(null) })
            }
            listOf(CarryOverRule.NextDay, CarryOverRule.NextWeek, CarryOverRule.NextMonth, CarryOverRule.NextYear).forEach {
                ChoiceLine(carryOverLabel(it, today), selected = rule == it, onClick = { onChange(it) })
            }

            ChoiceLine(
                label = if (rule is CarryOverRule.InDays) carryOverLabel(rule, today) else stringResource(R.string.carry_in_days_choice),
                selected = rule is CarryOverRule.InDays,
                onClick = { if (rule !is CarryOverRule.InDays) onChange(CarryOverRule.InDays(2)) },
            )
            if (rule is CarryOverRule.InDays) {
                Stepper(
                    value = rule.days,
                    onChange = { onChange(CarryOverRule.InDays(it)) },
                    range = 1..365,
                    lessLabel = stringResource(R.string.carry_days_less),
                    moreLabel = stringResource(R.string.carry_days_more),
                    modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                )
            }

            ChoiceLine(
                label = if (rule is CarryOverRule.OnDate) carryOverLabel(rule, today) else stringResource(R.string.carry_on_date_choice),
                selected = rule is CarryOverRule.OnDate,
                onClick = { showCalendar = true },
            )
            ChoiceLine(carryOverLabel(CarryOverRule.AskMe, today), selected = rule == CarryOverRule.AskMe, onClick = { onChange(CarryOverRule.AskMe) })
            ChoiceLine(carryOverLabel(CarryOverRule.DontCarry, today), selected = rule == CarryOverRule.DontCarry, onClick = { onChange(CarryOverRule.DontCarry) })

            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = stringResource(R.string.task_ok), onClick = onDismiss)
        }
    }

    if (showCalendar) {
        DatePickDialog(
            initial = (rule as? CarryOverRule.OnDate)?.date ?: today.plusDays(1),
            onPicked = { onChange(CarryOverRule.OnDate(it)) },
            onDismiss = { showCalendar = false },
        )
    }
}

/**
 * A sheet with one text field, for the few values that are words: a name, a
 * description, a note. The keyboard opens with it and its own done key
 * saves, and the phone keyboard's microphone can be used instead of typing.
 */
@Composable
fun TextEditSheet(
    title: String,
    initial: String,
    hint: String,
    singleLine: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val save = {
        onSave(text)
        onDismiss()
    }

    AnujBottomSheet(onDismiss = onDismiss, title = title) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(hint) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            maxLines = if (singleLine) 1 else 8,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.task_save), onClick = save)

        /** Inside the sheet, so it runs once the field above is on screen and can take the focus. */
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
