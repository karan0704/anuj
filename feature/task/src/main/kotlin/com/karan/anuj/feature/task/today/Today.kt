package com.karan.anuj.feature.task.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.task.DoneEntry
import com.karan.anuj.core.domain.task.ObserveTodayUseCase
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TodayItem
import com.karan.anuj.core.domain.task.TodayTasks
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.MinTouchTarget
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.ScreenPadding
import com.karan.anuj.core.ui.components.SecondaryButton
import com.karan.anuj.core.ui.components.SectionTitle
import com.karan.anuj.feature.task.R
import com.karan.anuj.feature.task.common.DatePickDialog
import com.karan.anuj.feature.task.common.Day
import com.karan.anuj.feature.task.common.DayClock
import com.karan.anuj.feature.task.common.SwipeActions
import com.karan.anuj.feature.task.common.TaskActions
import com.karan.anuj.feature.task.common.TaskActionsViewModel
import com.karan.anuj.feature.task.common.TaskRow
import com.karan.anuj.feature.task.common.UndoSnackbars
import com.karan.anuj.feature.task.quickadd.QuickAddSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** @property tasks null while the first read is still under way, so the empty message is not flashed */
data class TodayUiState(
    val day: Day,
    val tasks: TodayTasks?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    observeToday: ObserveTodayUseCase,
    actions: TaskActions,
) : TaskActionsViewModel(actions) {

    private val clock = DayClock(viewModelScope)

    val state: StateFlow<TodayUiState> = clock.day
        .flatMapLatest { day ->
            observeToday(day.date, day.startMillis, day.endMillis).map { TodayUiState(day, it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(clock.day.value, null))

    fun refreshDay() = clock.refresh()
}

/** Which sheet or dialog is open over the list. Task ids are kept as text so the choice survives rotation. */
private enum class TodayOverlay { NONE, QUICK_ADD, STUCK, ADD_STEP, RESCHEDULE }

/**
 * The list under the clock on the home screen: what was left undone, what is
 * due today, and what has been finished today.
 *
 * @param header drawn as the first item, so it scrolls away with the list
 */
@Composable
fun TodayTaskList(
    snackbar: SnackbarHostState,
    onOpenTask: (TaskId) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = state.day.date
    var overlay by rememberSaveable { mutableStateOf(TodayOverlay.NONE) }
    var overlayTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDone by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDay()
        onPauseOrDispose {}
    }
    UndoSnackbars(viewModel.undoable, snackbar, viewModel::undo)

    val tasks = state.tasks
    val overlayTask: Task? = tasks?.let { all ->
        (all.due + all.needsDecision).firstOrNull { it.task.id.value == overlayTaskId }?.task
    }
    val closeOverlay = {
        overlay = TodayOverlay.NONE
        overlayTaskId = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            /** Room at the bottom so the last row can scroll clear of the add button. */
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "header") { header() }

            if (tasks != null && tasks.needsDecision.isEmpty() && tasks.due.isEmpty() && tasks.done.isEmpty()) {
                item(key = "empty") { EmptyToday() }
            }

            if (tasks != null && tasks.needsDecision.isNotEmpty()) {
                item(key = "decide-title") { SectionTitle(stringResource(R.string.today_decide_title)) }
                items(tasks.needsDecision, key = { "decide-${it.task.id.value}" }) { item ->
                    Column {
                        TaskRow(
                            task = item.task,
                            today = today,
                            progress = item.progress,
                            onToggleDone = { viewModel.complete(item.task) },
                            onClick = { onOpenTask(item.task.id) },
                        )
                        DecisionChips(
                            onToday = { viewModel.moveTo(item.task, today, countsAsCarry = true) },
                            onTomorrow = { viewModel.moveTo(item.task, today.plusDays(1), countsAsCarry = true) },
                            onNextWeek = { viewModel.moveTo(item.task, today.plusWeeks(1), countsAsCarry = true) },
                            onDrop = { viewModel.delete(item.task) },
                        )
                    }
                }
            }

            if (tasks != null && tasks.due.isNotEmpty()) {
                item(key = "due-title") { SectionTitle(stringResource(R.string.today_due_title)) }
                items(tasks.due, key = { "due-${it.task.id.value}" }) { item ->
                    DueRow(
                        item = item,
                        today = today,
                        onDone = { viewModel.complete(item.task) },
                        onTomorrow = { viewModel.moveTo(item.task, today.plusDays(1), countsAsCarry = true) },
                        onOpen = { onOpenTask(item.task.id) },
                        onStuck = {
                            overlayTaskId = item.task.id.value
                            overlay = TodayOverlay.STUCK
                        },
                    )
                }
            }

            if (tasks != null && tasks.done.isNotEmpty()) {
                item(key = "done-title") {
                    DoneHeader(count = tasks.done.size, expanded = showDone, onToggle = { showDone = !showDone })
                }
                if (showDone) {
                    items(tasks.done, key = { "done-${it.key}" }) { entry ->
                        DoneRow(entry, onReopen = { entry.taskId?.let(viewModel::reopen) })
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { overlay = TodayOverlay.QUICK_ADD },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(ScreenPadding),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.quick_add_open))
        }
    }

    when (overlay) {
        TodayOverlay.NONE -> Unit
        TodayOverlay.QUICK_ADD -> QuickAddSheet(parentId = null, defaultDate = today, onDismiss = closeOverlay)
        TodayOverlay.ADD_STEP -> QuickAddSheet(
            parentId = overlayTaskId?.let(::TaskId),
            defaultDate = null,
            onDismiss = closeOverlay,
        )
        TodayOverlay.STUCK -> if (overlayTask != null) {
            StuckSheet(
                task = overlayTask,
                onBreakDown = { overlay = TodayOverlay.ADD_STEP },
                onReschedule = { overlay = TodayOverlay.RESCHEDULE },
                onDrop = {
                    viewModel.delete(overlayTask)
                    closeOverlay()
                },
                onDismiss = closeOverlay,
            )
        }
        TodayOverlay.RESCHEDULE -> if (overlayTask != null) {
            DatePickDialog(
                initial = today.plusDays(1),
                /** A day the user picks on purpose is a fresh start, so the carry count is not raised. */
                onPicked = { viewModel.moveTo(overlayTask, it, countsAsCarry = false) },
                onDismiss = closeOverlay,
            )
        }
    }

    /** If the task a sheet was opened for has left today's list (done elsewhere, moved), the sheet has nothing to act on. */
    val needsTask = overlay == TodayOverlay.STUCK || overlay == TodayOverlay.RESCHEDULE
    LaunchedEffect(needsTask, overlayTask == null, tasks == null) {
        if (needsTask && overlayTask == null && tasks != null) closeOverlay()
    }
}

@Composable
private fun EmptyToday() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.today_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.today_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DueRow(
    item: TodayItem,
    today: LocalDate,
    onDone: () -> Unit,
    onTomorrow: () -> Unit,
    onOpen: () -> Unit,
    onStuck: () -> Unit,
) {
    Column {
        SwipeActions(
            rightLabel = stringResource(R.string.task_swipe_done),
            onSwipeRight = onDone,
            leftLabel = stringResource(R.string.task_swipe_tomorrow),
            onSwipeLeft = onTomorrow,
        ) {
            TaskRow(task = item.task, today = today, progress = item.progress, onToggleDone = onDone, onClick = onOpen)
        }
        if (item.stuck) {
            AssistChip(
                onClick = onStuck,
                label = { Text(stringResource(R.string.today_stuck, item.task.carryCount)) },
                modifier = Modifier.padding(start = ScreenPadding + 36.dp, bottom = 4.dp),
            )
        }
    }
}

/** One-tap answers for a task whose day has passed, in place of a dialog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecisionChips(onToday: () -> Unit, onTomorrow: () -> Unit, onNextWeek: () -> Unit, onDrop: () -> Unit) {
    FlowRow(
        modifier = Modifier.padding(start = ScreenPadding + 36.dp, end = ScreenPadding, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(onClick = onToday, label = { Text(stringResource(R.string.task_today)) })
        AssistChip(onClick = onTomorrow, label = { Text(stringResource(R.string.task_tomorrow)) })
        AssistChip(onClick = onNextWeek, label = { Text(stringResource(R.string.task_next_week)) })
        AssistChip(onClick = onDrop, label = { Text(stringResource(R.string.today_decide_drop)) })
    }
}

@Composable
private fun DoneHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(onClick = onToggle)
            .padding(horizontal = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.today_done_title, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DoneRow(entry: DoneEntry, onReopen: () -> Unit) {
    val reopenLabel = stringResource(R.string.today_reopen)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (entry.taskId != null) Modifier.clickable(onClickLabel = reopenLabel, onClick = onReopen) else Modifier)
            .padding(horizontal = ScreenPadding + 36.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.LineThrough,
        )
    }
}

@Composable
private fun StuckSheet(
    task: Task,
    onBreakDown: () -> Unit,
    onReschedule: () -> Unit,
    onDrop: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnujBottomSheet(onDismiss = onDismiss, title = task.name) {
        Text(
            stringResource(R.string.today_stuck, task.carryCount),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.today_stuck_break), onClick = onBreakDown)
        Spacer(Modifier.height(8.dp))
        SecondaryButton(text = stringResource(R.string.today_stuck_reschedule), onClick = onReschedule)
        Spacer(Modifier.height(8.dp))
        SecondaryButton(text = stringResource(R.string.today_stuck_drop), onClick = onDrop)
    }
}
