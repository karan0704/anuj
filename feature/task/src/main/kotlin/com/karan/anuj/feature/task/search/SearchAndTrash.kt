package com.karan.anuj.feature.task.search

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.task.EmptyTrashUseCase
import com.karan.anuj.core.domain.task.ObserveTrashUseCase
import com.karan.anuj.core.domain.task.PurgeTasksUseCase
import com.karan.anuj.core.domain.task.RestoreTaskUseCase
import com.karan.anuj.core.domain.task.SearchTasksUseCase
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.ui.components.MinTouchTarget
import com.karan.anuj.core.ui.components.ScreenHeader
import com.karan.anuj.core.ui.components.ScreenPadding
import com.karan.anuj.feature.task.R
import com.karan.anuj.feature.task.common.Day
import com.karan.anuj.feature.task.common.TaskActions
import com.karan.anuj.feature.task.common.TaskActionsViewModel
import com.karan.anuj.feature.task.common.TaskRow
import com.karan.anuj.feature.task.common.UndoSnackbars
import com.karan.anuj.feature.task.common.WriteScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchTasks: SearchTasksUseCase,
    actions: TaskActions,
) : TaskActionsViewModel(actions) {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Raised after a task in the results is changed, so the list is searched again and shows the change. */
    private val refresh = MutableStateFlow(0)

    /**
     * Searching waits a moment after the last key, so each letter typed does
     * not start its own search; a newer search replaces one still running.
     */
    val results: StateFlow<List<Task>> = combine(_query.debounce(SEARCH_DELAY_MILLIS), refresh) { text, _ -> text }
        .mapLatest { searchTasks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(text: String) {
        _query.value = text
    }

    fun toggleDone(task: Task) {
        if (task.isOpen) complete(task) else reopen(task.id)
    }

    override fun onSaved() {
        refresh.value++
    }

    private companion object {
        const val SEARCH_DELAY_MILLIS = 200L
    }
}

@Composable
fun SearchScreen(
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenTask: (TaskId) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val today = Day.now().date
    val focus = remember { FocusRequester() }

    UndoSnackbars(viewModel.undoable, snackbar, viewModel::undo)

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.search_title),
            onBack = onBack,
            backLabel = stringResource(R.string.task_back),
        )
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding)
                .focusRequester(focus),
        )
        Spacer(Modifier.height(8.dp))

        if (query.isNotBlank() && results.isEmpty()) {
            Text(
                stringResource(R.string.search_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ScreenPadding),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.id.value }) { task ->
                    TaskRow(
                        task = task,
                        today = today,
                        onToggleDone = { viewModel.toggleDone(task) },
                        onClick = { onOpenTask(task.id) },
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}

@HiltViewModel
class TrashViewModel @Inject constructor(
    observeTrash: ObserveTrashUseCase,
    private val restoreTask: RestoreTaskUseCase,
    private val purgeTasks: PurgeTasksUseCase,
    private val emptyTrash: EmptyTrashUseCase,
    private val writes: WriteScope,
) : ViewModel() {

    /** Null until the first read has come back, so the empty message is not flashed. */
    val trashed: StateFlow<List<Task>?> =
        observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(id: TaskId) {
        writes.launch { restoreTask(id) }
    }

    fun deleteForever(id: TaskId) {
        writes.launch { purgeTasks(listOf(id)) }
    }

    fun empty() {
        writes.launch { emptyTrash() }
    }
}

/**
 * Restoring is one tap and needs no confirmation, because it loses nothing.
 * Deleting for good cannot be undone, so it is the one place a dialog asks first.
 */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val trashed by viewModel.trashed.collectAsStateWithLifecycle()
    /** The id of the task waiting for "Delete forever?" to be answered; kept as text so it survives rotation. */
    var confirmingId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmingAll by rememberSaveable { mutableStateOf(false) }
    val list = trashed

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.trash_title),
            onBack = onBack,
            backLabel = stringResource(R.string.task_back),
        ) {
            if (!list.isNullOrEmpty()) {
                TextButton(onClick = { confirmingAll = true }) { Text(stringResource(R.string.trash_empty_all)) }
            }
        }

        if (list != null && list.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.trash_empty_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.trash_empty_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(list.orEmpty(), key = { it.id.value }) { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MinTouchTarget)
                            .padding(start = ScreenPadding, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            task.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.restore(task.id) }) { Text(stringResource(R.string.trash_restore)) }
                        IconButton(onClick = { confirmingId = task.id.value }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.trash_delete_forever))
                        }
                    }
                }
            }
        }
    }

    val confirming = list?.firstOrNull { it.id.value == confirmingId }
    if (confirming != null) {
        ConfirmDialog(
            title = stringResource(R.string.trash_confirm_one_title),
            body = stringResource(R.string.trash_confirm_one_body, confirming.name),
            confirmLabel = stringResource(R.string.trash_delete_forever),
            onConfirm = { viewModel.deleteForever(confirming.id) },
            onDismiss = { confirmingId = null },
        )
    }
    if (confirmingAll) {
        ConfirmDialog(
            title = stringResource(R.string.trash_confirm_all_title),
            body = stringResource(R.string.trash_confirm_all_body),
            confirmLabel = stringResource(R.string.trash_empty_all),
            onConfirm = viewModel::empty,
            onDismiss = { confirmingAll = false },
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.task_cancel)) } },
    )
}
