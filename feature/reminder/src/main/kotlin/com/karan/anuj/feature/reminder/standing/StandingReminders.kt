package com.karan.anuj.feature.reminder.standing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.reminder.Reminder
import com.karan.anuj.core.domain.reminder.ReminderId
import com.karan.anuj.core.domain.reminder.ReminderSchedule
import com.karan.anuj.core.domain.reminder.ReminderSettings
import com.karan.anuj.core.domain.reminder.ReminderSettingsUseCase
import com.karan.anuj.core.domain.reminder.ReminderStyle
import com.karan.anuj.core.domain.reminder.StandingPresets
import com.karan.anuj.core.domain.reminder.StandingReminderDraft
import com.karan.anuj.core.domain.reminder.StandingRemindersUseCase
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.ChipSpacing
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.ClockDialog
import com.karan.anuj.core.ui.components.FieldRow
import com.karan.anuj.core.ui.components.MinTouchTarget
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.ScreenHeader
import com.karan.anuj.core.ui.components.ScreenPadding
import com.karan.anuj.core.ui.components.SecondaryButton
import com.karan.anuj.core.ui.components.ToggleChips
import com.karan.anuj.core.ui.components.clockLabel
import com.karan.anuj.feature.reminder.R
import com.karan.anuj.feature.reminder.common.GroupLabel
import com.karan.anuj.feature.reminder.common.NaggingPicker
import com.karan.anuj.feature.reminder.common.StylePicker
import com.karan.anuj.feature.reminder.common.ToneRow
import com.karan.anuj.feature.reminder.common.lengthLabel
import com.karan.anuj.feature.reminder.common.scheduleLabel
import com.karan.anuj.feature.reminder.common.shortLabel
import com.karan.anuj.feature.reminder.platform.ReminderRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StandingUiState(
    val reminders: List<Reminder> = emptyList(),
    val settings: ReminderSettings = ReminderSettings(),
)

@HiltViewModel
class StandingRemindersViewModel @Inject constructor(
    private val standing: StandingRemindersUseCase,
    settings: ReminderSettingsUseCase,
    private val runner: ReminderRunner,
) : ViewModel() {

    val state: StateFlow<StandingUiState> =
        combine(standing.observe(), settings.observe(), ::StandingUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StandingUiState())

    private val addedChannel = Channel<ReminderId>(Channel.BUFFERED)

    /** A reminder just made from a preset, so the screen can open it for adjusting. */
    val added: Flow<ReminderId> = addedChannel.receiveAsFlow()

    private val removedChannel = Channel<Reminder>(Channel.BUFFERED)

    /** A reminder just removed, for the "Undo" message. */
    val removed: Flow<Reminder> = removedChannel.receiveAsFlow()

    fun add(draft: StandingReminderDraft) {
        runner.launch { addedChannel.send(standing.add(draft).id) }
    }

    fun update(id: ReminderId, change: (Reminder) -> Reminder) {
        runner.launch { standing.update(id, change) }
    }

    fun remove(reminder: Reminder) {
        runner.launch {
            standing.remove(reminder)
            removedChannel.send(reminder)
        }
    }

    fun restore(reminder: Reminder) {
        runner.launch { standing.restore(reminder) }
    }
}

/** A ready-made reminder offered as a chip, with the label shown on the chip. */
private class Preset(val label: Int, val draft: StandingReminderDraft)

private val Presets = listOf(
    Preset(R.string.preset_medication, StandingPresets.medication),
    Preset(R.string.preset_water, StandingPresets.water),
    Preset(R.string.preset_meals, StandingPresets.meals),
    Preset(R.string.preset_custom, StandingPresets.custom),
)

/**
 * Reminders that are not tied to a task: medication, water, meals, or
 * anything else that comes round at set times. One is added by tapping a
 * ready-made chip and then adjusted; nothing has to be written.
 */
@Composable
fun StandingRemindersScreen(
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    viewModel: StandingRemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    /** The id of the reminder open in the sheet; kept as text so it survives rotation. */
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(viewModel) { viewModel.added.collect { editingId = it.value } }
    LaunchedEffect(viewModel, snackbar) {
        viewModel.removed.collect { reminder ->
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val result = snackbar.showSnackbar(
                    message = context.getString(R.string.regular_removed, reminder.title),
                    actionLabel = context.getString(R.string.reminder_undo),
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.restore(reminder)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.regular_title),
            onBack = onBack,
            backLabel = stringResource(R.string.reminder_back),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (state.reminders.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.regular_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 16.dp),
                    )
                }
            }
            items(state.reminders, key = { it.id.value }) { reminder ->
                StandingRow(
                    reminder = reminder,
                    onOpen = { editingId = reminder.id.value },
                    onEnabled = { on -> viewModel.update(reminder.id) { it.copy(enabled = on) } },
                )
            }
            item(key = "add") {
                Column(modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 16.dp)) {
                    GroupLabel(stringResource(R.string.regular_add))
                    Row(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
                        Presets.take(2).forEach { PresetChip(it, viewModel::add) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
                        Presets.drop(2).forEach { PresetChip(it, viewModel::add) }
                    }
                }
            }
        }
    }

    state.reminders.firstOrNull { it.id.value == editingId }?.let { reminder ->
        StandingEditorSheet(
            reminder = reminder,
            settings = state.settings,
            onChange = { change -> viewModel.update(reminder.id, change) },
            onRemove = {
                editingId = null
                viewModel.remove(reminder)
            },
            onDismiss = { editingId = null },
        )
    }
}

@Composable
private fun PresetChip(preset: Preset, onAdd: (StandingReminderDraft) -> Unit) {
    AssistChip(onClick = { onAdd(preset.draft) }, label = { Text(stringResource(preset.label)) })
}

@Composable
private fun StandingRow(reminder: Reminder, onOpen: () -> Unit, onEnabled: (Boolean) -> Unit) {
    val switchLabel = stringResource(R.string.regular_switch, reminder.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(onClick = onOpen)
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(reminder.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                scheduleLabel(reminder.schedule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = reminder.enabled,
            onCheckedChange = onEnabled,
            modifier = Modifier.semantics { contentDescription = switchLabel },
        )
    }
}

/** Which of the reminder's times the clock is open for. */
private enum class ClockFor { NEW_TIME, FROM, UNTIL }

/** The two rhythms a regular reminder can have, as chips. */
private enum class Rhythm { AT_TIMES, EVERY }

/**
 * One regular reminder. Every change is saved as it is made, so there is no
 * "save" to forget; "Remove" can be undone from the message that follows.
 */
@Composable
private fun StandingEditorSheet(
    reminder: Reminder,
    settings: ReminderSettings,
    onChange: ((Reminder) -> Reminder) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var clock by rememberSaveable { mutableStateOf<ClockFor?>(null) }
    /** The name is typed into a local copy and saved when the box is left, so each keystroke is not a database write. */
    var name by rememberSaveable(reminder.id.value) { mutableStateOf(reminder.title) }
    val schedule = reminder.schedule
    val setSchedule = { next: ReminderSchedule -> onChange { it.copy(schedule = next) } }
    val saveName = { if (name.isNotBlank() && name.trim() != reminder.title) onChange { it.copy(title = name.trim()) } }

    AnujBottomSheet(
        onDismiss = {
            saveName()
            onDismiss()
        },
        title = reminder.title,
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.regular_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            GroupLabel(stringResource(R.string.regular_rhythm))
            ChoiceChips(
                options = Rhythm.entries,
                selected = if (schedule is ReminderSchedule.Every) Rhythm.EVERY else Rhythm.AT_TIMES,
                label = { stringResource(if (it == Rhythm.EVERY) R.string.rhythm_every else R.string.rhythm_at_times) },
                onSelect = { rhythm -> setSchedule(schedule.asRhythm(rhythm)) },
            )

            when (schedule) {
                is ReminderSchedule.AtTimes -> {
                    GroupLabel(stringResource(R.string.regular_times_hint))
                    ToggleChips(
                        options = schedule.times.sorted(),
                        selected = schedule.times.toSet(),
                        label = { clockLabel(it) },
                        /** The last time cannot be removed: a reminder with no time would never go off. */
                        onToggle = { time -> if (schedule.times.size > 1) setSchedule(schedule.copy(times = schedule.times - time)) },
                    )
                    AssistChip(onClick = { clock = ClockFor.NEW_TIME }, label = { Text(stringResource(R.string.reminder_add_time)) })
                }
                is ReminderSchedule.Every -> {
                    GroupLabel(stringResource(R.string.regular_gap))
                    ChoiceChips(
                        options = (ReminderSettings.INTERVAL_CANDIDATES + schedule.everyMinutes).distinct().sorted(),
                        selected = schedule.everyMinutes,
                        label = { lengthLabel(it) },
                        onSelect = { setSchedule(schedule.copy(everyMinutes = it)) },
                    )
                    FieldRow(stringResource(R.string.regular_from), clockLabel(schedule.from), onClick = { clock = ClockFor.FROM })
                    FieldRow(stringResource(R.string.regular_until), clockLabel(schedule.until), onClick = { clock = ClockFor.UNTIL })
                }
                /** Task reminders and one-off reminders are not shown on this screen. */
                is ReminderSchedule.BeforeTask, is ReminderSchedule.Once -> Unit
            }

            GroupLabel(stringResource(R.string.regular_days_hint))
            ToggleChips(
                options = DayOfWeek.entries,
                selected = schedule.days(),
                label = { it.shortLabel() },
                onToggle = { day -> setSchedule(schedule.withDays(schedule.days().toggled(day))) },
            )

            NaggingPicker(
                nagging = reminder.nagging,
                gaps = settings.nagChoices,
                fallbackTimes = settings.defaultNagging?.times ?: DEFAULT_REPEATS,
                onChange = { nagging -> onChange { it.copy(nagging = nagging) } },
            )
            StylePicker(reminder.style) { style -> onChange { it.copy(style = style) } }
            ToneRow(
                toneUri = reminder.toneUri,
                unsetLabel = stringResource(R.string.tone_of_kind),
                alarm = reminder.style == ReminderStyle.ALARM,
                onPicked = { tone -> onChange { it.copy(toneUri = tone) } },
            )

            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = stringResource(R.string.reminder_ok),
                onClick = {
                    saveName()
                    onDismiss()
                },
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = stringResource(R.string.regular_remove), onClick = onRemove)
        }
    }

    clock?.let { target ->
        ClockDialog(
            initial = when (target) {
                ClockFor.NEW_TIME -> LocalTime.NOON
                ClockFor.FROM -> (schedule as? ReminderSchedule.Every)?.from ?: LocalTime.NOON
                ClockFor.UNTIL -> (schedule as? ReminderSchedule.Every)?.until ?: LocalTime.NOON
            },
            okLabel = stringResource(R.string.reminder_ok),
            cancelLabel = stringResource(R.string.reminder_cancel),
            onPicked = { time ->
                when {
                    target == ClockFor.NEW_TIME && schedule is ReminderSchedule.AtTimes ->
                        setSchedule(schedule.copy(times = (schedule.times + time).distinct()))
                    target == ClockFor.FROM && schedule is ReminderSchedule.Every -> setSchedule(schedule.copy(from = time))
                    target == ClockFor.UNTIL && schedule is ReminderSchedule.Every -> setSchedule(schedule.copy(until = time))
                }
            },
            onDismiss = { clock = null },
        )
    }
}

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

private fun ReminderSchedule.days(): Set<DayOfWeek> = when (this) {
    is ReminderSchedule.AtTimes -> days
    is ReminderSchedule.Every -> days
    is ReminderSchedule.BeforeTask, is ReminderSchedule.Once -> emptySet()
}

private fun ReminderSchedule.withDays(days: Set<DayOfWeek>): ReminderSchedule = when (this) {
    is ReminderSchedule.AtTimes -> copy(days = days)
    is ReminderSchedule.Every -> copy(days = days)
    is ReminderSchedule.BeforeTask, is ReminderSchedule.Once -> this
}

/** Switching rhythm keeps the chosen days and starts the other rhythm from its ready-made times. */
private fun ReminderSchedule.asRhythm(rhythm: Rhythm): ReminderSchedule = when {
    rhythm == Rhythm.EVERY && this !is ReminderSchedule.Every ->
        (StandingPresets.water.schedule as ReminderSchedule.Every).copy(days = days())
    rhythm == Rhythm.AT_TIMES && this !is ReminderSchedule.AtTimes ->
        (StandingPresets.custom.schedule as ReminderSchedule.AtTimes).copy(days = days())
    else -> this
}

private const val DEFAULT_REPEATS = 3
