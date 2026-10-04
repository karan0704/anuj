package com.karan.anuj.feature.reminder.task

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.reminder.ReminderSettings
import com.karan.anuj.core.domain.reminder.ReminderSettingsUseCase
import com.karan.anuj.core.domain.reminder.ReminderStyle
import com.karan.anuj.core.domain.reminder.TaskReminderPlan
import com.karan.anuj.core.domain.reminder.TaskRemindersUseCase
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.FieldRow
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.ToggleChips
import com.karan.anuj.feature.reminder.R
import com.karan.anuj.feature.reminder.common.NaggingPicker
import com.karan.anuj.feature.reminder.common.StylePicker
import com.karan.anuj.feature.reminder.common.ToneRow
import com.karan.anuj.feature.reminder.common.leadLabel
import com.karan.anuj.feature.reminder.platform.ReminderRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class TaskReminderUiState(
    val plan: TaskReminderPlan = TaskReminderPlan(),
    val settings: ReminderSettings = ReminderSettings(),
)

@HiltViewModel
class TaskReminderViewModel @Inject constructor(
    private val taskReminders: TaskRemindersUseCase,
    settings: ReminderSettingsUseCase,
    private val runner: ReminderRunner,
) : ViewModel() {

    private val taskId = MutableStateFlow<TaskId?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TaskReminderUiState> =
        combine(taskId.filterNotNull().flatMapLatest(taskReminders::observePlan), settings.observe(), ::TaskReminderUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskReminderUiState())

    /** The task comes from the screen this row sits in, not from navigation, so it is handed over once the row is drawn. */
    fun show(id: TaskId) {
        taskId.value = id
    }

    /** The first time chosen brings the user's usual repeat with it, so one tap gives a reminder that keeps at it. */
    fun toggleLead(minutes: Int) {
        val current = state.value
        val plan = current.plan
        setPlan(
            when {
                minutes in plan.leads -> plan.copy(leads = plan.leads - minutes)
                plan.leads.isEmpty() -> plan.copy(leads = setOf(minutes), nagging = current.settings.defaultNagging)
                else -> plan.copy(leads = plan.leads + minutes)
            },
        )
    }

    fun setPlan(plan: TaskReminderPlan) {
        val id = taskId.value ?: return
        runner.launch { taskReminders.setPlan(id, plan) }
    }
}

/**
 * The "Remind me" row of a task and the sheet behind it. It is drawn inside
 * the task screen but belongs to this feature: the task screen only leaves a
 * place for it, so tasks and reminders stay independent of each other.
 *
 * @param hasDay false while the task has no day, when there is nothing to time a reminder from
 */
@Composable
fun TaskReminderField(taskId: TaskId, hasDay: Boolean) {
    val viewModel = hiltViewModel<TaskReminderViewModel>(key = "task-reminder-${taskId.value}")
    LaunchedEffect(taskId) { viewModel.show(taskId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plan = state.plan
    var open by rememberSaveable { mutableStateOf(false) }

    FieldRow(
        label = stringResource(R.string.remind_title),
        value = when {
            plan.leads.isEmpty() -> stringResource(R.string.remind_off)
            !hasDay -> stringResource(R.string.remind_needs_day)
            else -> summaryOf(plan)
        },
        onClick = { open = true },
    )

    if (open) {
        AnujBottomSheet(onDismiss = { open = false }, title = stringResource(R.string.remind_title)) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ToggleChips(
                    /** A time this task already has stays on offer after it has been taken out of the settings. */
                    options = (state.settings.leadChoices + plan.leads).distinct().sorted(),
                    selected = plan.leads,
                    label = { leadLabel(it) },
                    onToggle = viewModel::toggleLead,
                )
                if (plan.leads.isNotEmpty()) {
                    NaggingPicker(
                        nagging = plan.nagging,
                        gaps = state.settings.nagChoices,
                        fallbackTimes = state.settings.defaultNagging?.times ?: DEFAULT_REPEATS,
                        onChange = { viewModel.setPlan(plan.copy(nagging = it)) },
                    )
                    StylePicker(plan.style) { viewModel.setPlan(plan.copy(style = it)) }
                    ToneRow(
                        toneUri = plan.toneUri,
                        unsetLabel = stringResource(R.string.tone_of_kind),
                        alarm = plan.style == ReminderStyle.ALARM,
                        onPicked = { viewModel.setPlan(plan.copy(toneUri = it)) },
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = stringResource(R.string.reminder_ok), onClick = { open = false })
            }
        }
    }
}

/** "At the time · 15 min before", with "· Alarm" when it rings full screen. */
@Composable
private fun summaryOf(plan: TaskReminderPlan): String {
    val times = plan.leads.sorted().map { leadLabel(it) }
    val alarm = listOfNotNull(stringResource(R.string.style_alarm).takeIf { plan.style == ReminderStyle.ALARM })
    return (times + alarm).joinToString(" · ")
}

private const val DEFAULT_REPEATS = 3
