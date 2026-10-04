package com.karan.anuj.feature.reminder.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.reminder.CategorySettings
import com.karan.anuj.core.domain.reminder.Delivery
import com.karan.anuj.core.domain.reminder.ReminderCategory
import com.karan.anuj.core.domain.reminder.ReminderSettings
import com.karan.anuj.core.domain.reminder.ReminderSettingsUseCase
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.ClockDialog
import com.karan.anuj.core.ui.components.FieldRow
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.ScreenHeader
import com.karan.anuj.core.ui.components.SectionTitle
import com.karan.anuj.core.ui.components.SwitchRow
import com.karan.anuj.core.ui.components.ToggleChips
import com.karan.anuj.core.ui.components.clockLabel
import com.karan.anuj.feature.reminder.R
import com.karan.anuj.feature.reminder.common.GroupLabel
import com.karan.anuj.feature.reminder.common.NaggingPicker
import com.karan.anuj.feature.reminder.common.ToneRow
import com.karan.anuj.feature.reminder.common.labelRes
import com.karan.anuj.feature.reminder.common.leadLabel
import com.karan.anuj.feature.reminder.common.lengthLabel
import com.karan.anuj.feature.reminder.common.naggingLabel
import com.karan.anuj.feature.reminder.platform.ReminderRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    private val settings: ReminderSettingsUseCase,
    private val runner: ReminderRunner,
) : ViewModel() {

    val state: StateFlow<ReminderSettings> =
        settings.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettings())

    fun update(change: (ReminderSettings) -> ReminderSettings) {
        runner.launch { settings.update(change) }
    }
}

/**
 * The reminder rows of the main Settings screen: calm mode as a switch,
 * because it is the one setting wanted in a hurry, and three doors to the
 * screens that hold the rest.
 */
@Composable
fun ReminderSettingsRows(
    onOpenNotifications: () -> Unit,
    onOpenRegular: () -> Unit,
    onOpenCheck: () -> Unit,
    viewModel: ReminderSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()

    SwitchRow(
        label = stringResource(R.string.calm_title),
        detail = stringResource(R.string.calm_detail),
        checked = settings.calmMode,
        onChange = { on -> viewModel.update { it.copy(calmMode = on) } },
    )
    FieldRow(stringResource(R.string.settings_notifications), stringResource(R.string.settings_notifications_detail), onOpenNotifications)
    FieldRow(stringResource(R.string.regular_title), stringResource(R.string.regular_detail), onOpenRegular)
    FieldRow(stringResource(R.string.check_title), stringResource(R.string.check_detail), onOpenCheck)
}

/** Which sheet is open over the notification settings. */
private enum class OpenSheet { NONE, DAILY_LIMIT, SUMMARY_TIMES, AUTO_REPEAT, LEADS, SNOOZES, GAPS, REASONS, ROUTINE_WARNING }

/** Which time the clock is open for. */
private enum class ClockFor { QUIET_FROM, QUIET_UNTIL, ALL_DAY, SUMMARY }

/**
 * Everything that decides how much the app may interrupt, and the choices
 * offered when a reminder is set up. Every value is picked from chips, a
 * switch or a clock; only a new reason for snoozing is written.
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: ReminderSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(OpenSheet.NONE) }
    var clock by rememberSaveable { mutableStateOf<ClockFor?>(null) }
    var category by rememberSaveable { mutableStateOf<ReminderCategory?>(null) }
    val closeSheet = { sheet = OpenSheet.NONE }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.settings_notifications),
            onBack = onBack,
            backLabel = stringResource(R.string.reminder_back),
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.section_quiet))
            SwitchRow(
                label = stringResource(R.string.calm_title),
                detail = stringResource(R.string.calm_detail),
                checked = settings.calmMode,
                onChange = { on -> viewModel.update { it.copy(calmMode = on) } },
            )
            SwitchRow(
                label = stringResource(R.string.quiet_title),
                detail = stringResource(R.string.quiet_detail),
                checked = settings.quietHours.enabled,
                onChange = { on -> viewModel.update { it.copy(quietHours = it.quietHours.copy(enabled = on)) } },
            )
            if (settings.quietHours.enabled) {
                FieldRow(stringResource(R.string.quiet_from), clockLabel(settings.quietHours.from), onClick = { clock = ClockFor.QUIET_FROM })
                FieldRow(stringResource(R.string.quiet_until), clockLabel(settings.quietHours.until), onClick = { clock = ClockFor.QUIET_UNTIL })
            }
            FieldRow(stringResource(R.string.limit_title), limitLabel(settings.dailyLimit), onClick = { sheet = OpenSheet.DAILY_LIMIT })
            FieldRow(
                label = stringResource(R.string.summary_times_title),
                value = settings.summaryTimes.joinToString(", ") { clockLabel(it) }.ifEmpty { stringResource(R.string.summary_times_none) },
                onClick = { sheet = OpenSheet.SUMMARY_TIMES },
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.section_kinds))
            ReminderCategory.entries.forEach { kind ->
                FieldRow(stringResource(kind.labelRes()), categorySummary(settings.category(kind)), onClick = { category = kind })
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.section_tasks))
            SwitchRow(
                label = stringResource(R.string.auto_title),
                detail = stringResource(R.string.auto_detail),
                checked = settings.autoRemindTimedTasks,
                onChange = { on -> viewModel.update { it.copy(autoRemindTimedTasks = on) } },
            )
            FieldRow(stringResource(R.string.auto_repeat_title), naggingLabel(settings.defaultNagging), onClick = { sheet = OpenSheet.AUTO_REPEAT })
            FieldRow(stringResource(R.string.all_day_title), clockLabel(settings.allDayTime), onClick = { clock = ClockFor.ALL_DAY })

            HorizontalDivider()
            SectionTitle(stringResource(R.string.section_choices))
            FieldRow(
                label = stringResource(R.string.choices_leads),
                value = settings.leadChoices.map { leadLabel(it) }.joinToString(" · "),
                onClick = { sheet = OpenSheet.LEADS },
            )
            FieldRow(
                label = stringResource(R.string.choices_snoozes),
                value = settings.snoozeChoices.map { lengthLabel(it) }.joinToString(" · "),
                onClick = { sheet = OpenSheet.SNOOZES },
            )
            FieldRow(
                label = stringResource(R.string.choices_gaps),
                value = settings.nagChoices.map { lengthLabel(it) }.joinToString(" · "),
                onClick = { sheet = OpenSheet.GAPS },
            )
            FieldRow(
                label = stringResource(R.string.choices_reasons),
                value = settings.snoozeReasons.joinToString(" · ").ifEmpty { stringResource(R.string.reasons_none) },
                onClick = { sheet = OpenSheet.REASONS },
            )
            FieldRow(
                label = stringResource(R.string.routine_warning_title),
                value = warningLabel(settings.routineWarningMinutes),
                onClick = { sheet = OpenSheet.ROUTINE_WARNING },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    when (sheet) {
        OpenSheet.NONE -> Unit
        OpenSheet.DAILY_LIMIT -> ChipSheet(stringResource(R.string.limit_title), closeSheet) {
            ChoiceChips(
                options = (ReminderSettings.DAILY_LIMIT_CHOICES + settings.dailyLimit).distinct().sorted(),
                selected = settings.dailyLimit,
                label = { limitLabel(it) },
                onSelect = { limit -> viewModel.update { it.copy(dailyLimit = limit) } },
            )
        }
        OpenSheet.SUMMARY_TIMES -> ChipSheet(stringResource(R.string.summary_times_title), closeSheet) {
            GroupLabel(stringResource(R.string.summary_times_hint))
            ToggleChips(
                options = settings.summaryTimes,
                selected = settings.summaryTimes.toSet(),
                label = { clockLabel(it) },
                onToggle = { time -> viewModel.update { it.copy(summaryTimes = it.summaryTimes - time) } },
            )
            AssistChip(onClick = { clock = ClockFor.SUMMARY }, label = { Text(stringResource(R.string.reminder_add_time)) })
        }
        OpenSheet.AUTO_REPEAT -> ChipSheet(stringResource(R.string.auto_repeat_title), closeSheet) {
            NaggingPicker(
                nagging = settings.defaultNagging,
                gaps = settings.nagChoices,
                fallbackTimes = DEFAULT_REPEATS,
                onChange = { nagging -> viewModel.update { it.copy(defaultNagging = nagging) } },
            )
        }
        OpenSheet.LEADS -> CandidateSheet(
            title = stringResource(R.string.choices_leads),
            candidates = ReminderSettings.LEAD_CANDIDATES,
            chosen = settings.leadChoices,
            label = { leadLabel(it) },
            onChange = { chosen -> viewModel.update { it.copy(leadChoices = chosen) } },
            onDismiss = closeSheet,
        )
        OpenSheet.SNOOZES -> CandidateSheet(
            title = stringResource(R.string.choices_snoozes),
            candidates = ReminderSettings.SNOOZE_CANDIDATES,
            chosen = settings.snoozeChoices,
            label = { lengthLabel(it) },
            onChange = { chosen -> viewModel.update { it.copy(snoozeChoices = chosen) } },
            onDismiss = closeSheet,
        ) {
            GroupLabel(stringResource(R.string.snooze_one_tap))
            ChoiceChips(
                options = (settings.snoozeChoices + settings.defaultSnoozeMinutes).distinct().sorted(),
                selected = settings.defaultSnoozeMinutes,
                label = { lengthLabel(it) },
                onSelect = { minutes -> viewModel.update { it.copy(defaultSnoozeMinutes = minutes) } },
            )
        }
        OpenSheet.GAPS -> CandidateSheet(
            title = stringResource(R.string.choices_gaps),
            candidates = ReminderSettings.NAG_CANDIDATES,
            chosen = settings.nagChoices,
            label = { lengthLabel(it) },
            onChange = { chosen -> viewModel.update { it.copy(nagChoices = chosen) } },
            onDismiss = closeSheet,
        )
        OpenSheet.REASONS -> ReasonsSheet(
            reasons = settings.snoozeReasons,
            onChange = { reasons -> viewModel.update { it.copy(snoozeReasons = reasons) } },
            onDismiss = closeSheet,
        )
        OpenSheet.ROUTINE_WARNING -> ChipSheet(stringResource(R.string.routine_warning_title), closeSheet) {
            ChoiceChips(
                options = (ReminderSettings.ROUTINE_WARNING_CHOICES + settings.routineWarningMinutes).distinct().sorted(),
                selected = settings.routineWarningMinutes,
                label = { warningLabel(it) },
                onSelect = { minutes -> viewModel.update { it.copy(routineWarningMinutes = minutes) } },
            )
        }
    }

    category?.let { kind ->
        CategorySheet(
            kind = kind,
            settings = settings.category(kind),
            onChange = { change -> viewModel.update { it.withCategory(kind, change) } },
            onDismiss = { category = null },
        )
    }

    clock?.let { target ->
        ClockDialog(
            initial = when (target) {
                ClockFor.QUIET_FROM -> settings.quietHours.from
                ClockFor.QUIET_UNTIL -> settings.quietHours.until
                ClockFor.ALL_DAY -> settings.allDayTime
                ClockFor.SUMMARY -> LocalTime.NOON
            },
            okLabel = stringResource(R.string.reminder_ok),
            cancelLabel = stringResource(R.string.reminder_cancel),
            onPicked = { time ->
                viewModel.update {
                    when (target) {
                        ClockFor.QUIET_FROM -> it.copy(quietHours = it.quietHours.copy(from = time))
                        ClockFor.QUIET_UNTIL -> it.copy(quietHours = it.quietHours.copy(until = time))
                        ClockFor.ALL_DAY -> it.copy(allDayTime = time)
                        ClockFor.SUMMARY -> it.copy(summaryTimes = it.summaryTimes + time)
                    }
                }
            },
            onDismiss = { clock = null },
        )
    }
}

@Composable
private fun limitLabel(limit: Int): String =
    if (limit <= 0) stringResource(R.string.limit_none) else stringResource(R.string.limit_value, limit)

@Composable
private fun warningLabel(minutes: Int): String =
    if (minutes <= 0) stringResource(R.string.routine_warning_off) else stringResource(R.string.routine_warning_value, lengthLabel(minutes))

/** "On · Interrupts", "On · Waits for the summary", or "Off". */
@Composable
private fun categorySummary(settings: CategorySettings): String = when {
    !settings.enabled -> stringResource(R.string.kind_off)
    settings.delivery == Delivery.SUMMARY -> stringResource(R.string.kind_on_summary)
    else -> stringResource(R.string.kind_on_now)
}

/** A sheet of chips with an OK button under them. */
@Composable
private fun ChipSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AnujBottomSheet(onDismiss = onDismiss, title = title) {
        content()
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.reminder_ok), onClick = onDismiss)
    }
}

/**
 * A list of chips offered elsewhere, edited by switching candidates on and
 * off. The last one cannot be switched off: the rules refuse an empty list,
 * so there is always something to tap.
 */
@Composable
private fun CandidateSheet(
    title: String,
    candidates: List<Int>,
    chosen: List<Int>,
    label: @Composable (Int) -> String,
    onChange: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
    below: @Composable () -> Unit = {},
) {
    ChipSheet(title, onDismiss) {
        ToggleChips(
            options = (candidates + chosen).distinct().sorted(),
            selected = chosen.toSet(),
            label = label,
            onToggle = { value -> onChange(if (value in chosen) chosen - value else chosen + value) },
        )
        below()
    }
}

/** The rules of one kind of notification. The summary cannot be moved to the summary, so it has no "when" choice. */
@Composable
private fun CategorySheet(
    kind: ReminderCategory,
    settings: CategorySettings,
    onChange: ((CategorySettings) -> CategorySettings) -> Unit,
    onDismiss: () -> Unit,
) {
    AnujBottomSheet(onDismiss = onDismiss, title = stringResource(kind.labelRes())) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SwitchRow(stringResource(R.string.kind_enabled), settings.enabled, { on -> onChange { it.copy(enabled = on) } })
            if (settings.enabled) {
                if (kind != ReminderCategory.SUMMARY) {
                    GroupLabel(stringResource(R.string.kind_when))
                    ChoiceChips(
                        options = Delivery.entries,
                        selected = settings.delivery,
                        label = {
                            stringResource(if (it == Delivery.NOW) R.string.kind_delivery_now else R.string.kind_delivery_summary)
                        },
                        onSelect = { delivery -> onChange { it.copy(delivery = delivery) } },
                    )
                }
                SwitchRow(stringResource(R.string.kind_vibrate), settings.vibrate, { on -> onChange { it.copy(vibrate = on) } })
                SwitchRow(
                    label = stringResource(R.string.kind_breaks_quiet),
                    checked = settings.breaksQuiet,
                    onChange = { on -> onChange { it.copy(breaksQuiet = on) } },
                )
                SwitchRow(
                    label = stringResource(R.string.kind_counts),
                    checked = settings.countsTowardLimit,
                    onChange = { on -> onChange { it.copy(countsTowardLimit = on) } },
                )
                ToneRow(
                    toneUri = settings.toneUri,
                    unsetLabel = stringResource(R.string.tone_default),
                    alarm = kind == ReminderCategory.ALARM,
                    onPicked = { tone -> onChange { it.copy(toneUri = tone) } },
                )
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = stringResource(R.string.reminder_ok), onClick = onDismiss)
        }
    }
}

/**
 * The reasons offered when snoozing. Tapping one removes it; a new one is
 * the only thing on these screens that is written, and it can be dictated
 * with the keyboard's microphone.
 */
@Composable
private fun ReasonsSheet(reasons: List<String>, onChange: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = {
        if (text.isNotBlank()) {
            onChange(reasons + text.trim())
            text = ""
        }
    }
    ChipSheet(stringResource(R.string.choices_reasons), onDismiss) {
        GroupLabel(stringResource(R.string.reasons_hint))
        ToggleChips(options = reasons, selected = reasons.toSet(), label = { it }, onToggle = { onChange(reasons - it) })
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.reasons_add)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = add, enabled = text.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.reasons_add))
            }
        }
    }
}

private const val DEFAULT_REPEATS = 3
