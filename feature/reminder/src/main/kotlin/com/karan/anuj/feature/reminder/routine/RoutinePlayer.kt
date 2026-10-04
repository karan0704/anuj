package com.karan.anuj.feature.reminder.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.reminder.ReminderSettingsUseCase
import com.karan.anuj.core.domain.reminder.ZoneSource
import com.karan.anuj.core.domain.task.CompleteTaskUseCase
import com.karan.anuj.core.domain.task.ObserveTaskDetailUseCase
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.ScreenHeader
import com.karan.anuj.core.ui.components.ScreenPadding
import com.karan.anuj.core.ui.components.SecondaryButton
import com.karan.anuj.feature.reminder.R
import com.karan.anuj.feature.reminder.common.lengthLabel
import com.karan.anuj.feature.reminder.platform.ReminderRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * @property name the routine's name; null until it has been read
 * @property current the step to do now; null when every step is done or skipped
 * @property next the step after it, named in the "time nearly up" warning
 * @property done steps finished so far, of [total]
 * @property warningMinutes how long before a step's time is up the warning shows; zero for never
 */
data class RoutineUiState(
    val name: String? = null,
    val current: Task? = null,
    val next: Task? = null,
    val done: Int = 0,
    val total: Int = 0,
    val warningMinutes: Int = 0,
)

@HiltViewModel
class RoutinePlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    observeDetail: ObserveTaskDetailUseCase,
    settings: ReminderSettingsUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val zones: ZoneSource,
    private val runner: ReminderRunner,
) : ViewModel() {

    private val routineId = TaskId(checkNotNull(savedState.get<String>(TASK_ID_ARG)) { "Routine opened without a task id" })

    /** Steps put off for now. They are not changed in any way: they are simply passed over until the screen is opened again. */
    private val skipped = MutableStateFlow<Set<TaskId>>(emptySet())

    val state: StateFlow<RoutineUiState> =
        combine(observeDetail(routineId), skipped, settings.observe()) { detail, passedOver, prefs ->
            val steps = detail?.children.orEmpty()
            val waiting = steps.filter { it.isOpen && it.id !in passedOver }
            RoutineUiState(
                name = detail?.task?.name,
                current = waiting.firstOrNull(),
                next = waiting.getOrNull(1),
                done = steps.count { it.isDone },
                total = steps.size,
                warningMinutes = prefs.routineWarningMinutes,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineUiState())

    fun finish(step: Task) {
        runner.launch { completeTask(step.id, LocalDate.now(zones.zone())) }
    }

    fun skip(step: Task) = skipped.update { it + step.id }

    companion object {
        /** The name of the navigation argument carrying the routine's task id. */
        const val TASK_ID_ARG = "routineId"
    }
}

/**
 * A task's steps, one at a time and large, so the only thing to decide is
 * "done" or "not now". A step with a time estimate counts down, and shortly
 * before its time is up the screen says what comes next, so the switch to
 * the next step does not arrive as a surprise.
 */
@Composable
fun RoutinePlayerScreen(
    onBack: () -> Unit,
    viewModel: RoutinePlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val step = state.current

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = state.name.orEmpty(), onBack = onBack, backLabel = stringResource(R.string.reminder_back))
        if (state.total > 0) {
            LinearProgressIndicator(
                progress = { state.done.toFloat() / state.total },
                modifier = Modifier
                    .padding(horizontal = ScreenPadding)
                    .fillMaxWidth()
                    .height(6.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (step == null) {
                Text(
                    stringResource(if (state.done == state.total) R.string.routine_all_done else R.string.routine_none_left),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                PrimaryButton(text = stringResource(R.string.reminder_back), onClick = onBack)
            } else {
                Text(
                    stringResource(R.string.routine_step_of, state.done + 1, state.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(step.name, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                StepTimer(step, state.next, state.warningMinutes)
                Spacer(Modifier.height(32.dp))
                PrimaryButton(text = stringResource(R.string.routine_done_next), onClick = { viewModel.finish(step) })
                Spacer(Modifier.height(8.dp))
                SecondaryButton(text = stringResource(R.string.routine_skip), onClick = { viewModel.skip(step) })
            }
        }
    }
}

/**
 * Counts a step's estimated time down while its screen is open. The start
 * moment is saved, so rotating the phone does not start the count again.
 * A step with no estimate shows no timer.
 */
@Composable
private fun StepTimer(step: Task, next: Task?, warningMinutes: Int) {
    val estimate = step.estimatedMinutes ?: return
    val haptics = LocalHapticFeedback.current
    var startedAt by rememberSaveable(step.id.value) { mutableLongStateOf(System.currentTimeMillis()) }
    var now by rememberSaveable(step.id.value) { mutableLongStateOf(startedAt) }

    LaunchedEffect(step.id.value) {
        while (true) {
            now = System.currentTimeMillis()
            delay(TICK_MILLIS)
        }
    }

    val leftSeconds = estimate * SECONDS_PER_MINUTE - (now - startedAt) / MILLIS_PER_SECOND
    val warn = warningMinutes > 0 && leftSeconds in 1..warningMinutes * SECONDS_PER_MINUTE
    /** One buzz as the warning starts, not one every second while it shows. */
    LaunchedEffect(warn) { if (warn) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    Text(
        text = if (leftSeconds > 0) {
            stringResource(R.string.routine_left, leftSeconds / SECONDS_PER_MINUTE, leftSeconds % SECONDS_PER_MINUTE)
        } else {
            stringResource(R.string.routine_over)
        },
        style = MaterialTheme.typography.headlineSmall,
        color = if (leftSeconds > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
    )
    if (warn && next != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.routine_then, lengthLabel(warningMinutes), next.name),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

private const val TICK_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60
