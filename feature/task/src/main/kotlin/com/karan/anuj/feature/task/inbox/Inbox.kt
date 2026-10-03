package com.karan.anuj.feature.task.inbox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.task.CreateTaskUseCase
import com.karan.anuj.core.domain.task.ObserveInboxUseCase
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskDraft
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.ui.components.MinTouchTarget
import com.karan.anuj.core.ui.components.ScreenHeader
import com.karan.anuj.core.ui.components.ScreenPadding
import com.karan.anuj.feature.task.R
import com.karan.anuj.feature.task.common.Day
import com.karan.anuj.feature.task.common.SwipeActions
import com.karan.anuj.feature.task.common.TaskActions
import com.karan.anuj.feature.task.common.TaskActionsViewModel
import com.karan.anuj.feature.task.common.TaskRow
import com.karan.anuj.feature.task.common.UndoSnackbars
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class InboxViewModel @Inject constructor(
    observeInbox: ObserveInboxUseCase,
    private val createTask: CreateTaskUseCase,
    private val actions: TaskActions,
) : TaskActionsViewModel(actions) {

    /** Null until the first read has come back, so the empty message is not flashed. */
    val thoughts: StateFlow<List<Task>?> =
        observeInbox().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun capture(text: String) {
        actions.writes.launch { createTask(TaskDraft(name = text), today = Day.now().date) }
    }
}

/**
 * Laid out like a chat: thoughts listed above, a text box pinned to the
 * bottom that rides up with the keyboard. The box keeps the focus after
 * each thought, so several can be caught one after another.
 */
@Composable
fun InboxScreen(
    snackbar: SnackbarHostState,
    onOpenTask: (TaskId) -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val thoughts by viewModel.thoughts.collectAsStateWithLifecycle()
    val today = Day.now().date

    UndoSnackbars(viewModel.undoable, snackbar, viewModel::undo)

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.inbox_title))

        val list = thoughts
        if (list != null && list.isEmpty()) {
            EmptyInbox(Modifier.weight(1f))
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(list.orEmpty(), key = { it.id.value }) { task ->
                    SwipeActions(
                        rightLabel = stringResource(R.string.task_swipe_done),
                        onSwipeRight = { viewModel.complete(task) },
                        leftLabel = stringResource(R.string.task_swipe_tomorrow),
                        onSwipeLeft = { viewModel.moveTo(task, today.plusDays(1), countsAsCarry = false) },
                    ) {
                        TaskRow(
                            task = task,
                            today = today,
                            onToggleDone = { viewModel.complete(task) },
                            onClick = { onOpenTask(task.id) },
                            /** Giving a thought a day is what sorts it, so "Today" is one tap away on every row. */
                            trailing = {
                                AssistChip(
                                    onClick = { viewModel.moveTo(task, today, countsAsCarry = false) },
                                    label = { Text(stringResource(R.string.task_today)) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            },
                        )
                    }
                }
            }
        }

        ThoughtBox(onCapture = viewModel::capture)
    }
}

@Composable
private fun ThoughtBox(onCapture: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (text.isNotBlank()) {
            onCapture(text)
            text = ""
        }
    }

    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.inbox_hint)) },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = send,
                enabled = text.isNotBlank(),
                modifier = Modifier.size(MinTouchTarget),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.inbox_send))
            }
        }
    }
}

@Composable
private fun EmptyInbox(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.inbox_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.inbox_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
