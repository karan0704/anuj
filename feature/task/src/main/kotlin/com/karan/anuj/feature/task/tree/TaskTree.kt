package com.karan.anuj.feature.task.tree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.task.ChildProgress
import com.karan.anuj.core.domain.task.ObserveTaskTreeUseCase
import com.karan.anuj.core.domain.task.Tag
import com.karan.anuj.core.domain.task.TagActions
import com.karan.anuj.core.domain.task.TagId
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TaskNode
import com.karan.anuj.core.domain.task.TaskTree
import com.karan.anuj.core.ui.components.ScreenHeader
import com.karan.anuj.core.ui.components.ScreenPadding
import com.karan.anuj.feature.task.R
import com.karan.anuj.feature.task.common.Day
import com.karan.anuj.feature.task.common.DayClock
import com.karan.anuj.feature.task.common.SwipeActions
import com.karan.anuj.feature.task.common.TagDot
import com.karan.anuj.feature.task.common.TaskActions
import com.karan.anuj.feature.task.common.TaskActionsViewModel
import com.karan.anuj.feature.task.common.TaskRow
import com.karan.anuj.feature.task.common.UndoSnackbars
import com.karan.anuj.feature.task.quickadd.QuickAddSheet
import com.karan.anuj.feature.task.templates.TemplateSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * @property rows the tree as the lines to draw, top to bottom
 * @property loaded false until the first read has come back, so the empty message is not flashed
 */
data class TaskTreeUiState(
    val day: Day,
    val rows: List<TaskNode> = emptyList(),
    val progress: Map<TaskId, ChildProgress> = emptyMap(),
    val tags: List<Tag> = emptyList(),
    val tagFilter: TagId? = null,
    val showFinished: Boolean = false,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskTreeViewModel @Inject constructor(
    observeTree: ObserveTaskTreeUseCase,
    tagActions: TagActions,
    actions: TaskActions,
) : TaskActionsViewModel(actions) {

    /** What the user has chosen about how the tree is shown. */
    private data class ViewChoices(
        val expanded: Set<TaskId> = emptySet(),
        val tagFilter: TagId? = null,
        val showFinished: Boolean = false,
    )

    private val clock = DayClock(viewModelScope)
    private val choices = MutableStateFlow(ViewChoices())

    val state: StateFlow<TaskTreeUiState> =
        combine(
            choices.flatMapLatest { observeTree(includeFinished = it.showFinished) },
            tagActions.observe(),
            choices,
            clock.day,
        ) { tree, tags, chosen, day ->
            /**
             * With a tag picked, only tasks carrying it are kept. Their
             * parents usually do not carry it, and the tree layout then
             * shows such tasks at the top level instead of hiding them.
             */
            val visible = chosen.tagFilter?.let { tag -> tree.tasks.filter { tag in it.tagIds } } ?: tree.tasks
            TaskTreeUiState(
                day = day,
                rows = TaskTree.flatten(visible, chosen.expanded),
                progress = tree.progress,
                tags = tags,
                tagFilter = chosen.tagFilter?.takeIf { tag -> tags.any { it.id == tag } },
                showFinished = chosen.showFinished,
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskTreeUiState(clock.day.value))

    fun toggleExpanded(id: TaskId) = choices.update {
        it.copy(expanded = if (id in it.expanded) it.expanded - id else it.expanded + id)
    }

    fun setTagFilter(tag: TagId?) = choices.update { it.copy(tagFilter = tag) }

    fun setShowFinished(show: Boolean) = choices.update { it.copy(showFinished = show) }

    fun refreshDay() = clock.refresh()
}

/** Which sheet is open over the tree. */
private enum class TreeOverlay { NONE, QUICK_ADD, TEMPLATES }

@Composable
fun TaskTreeScreen(
    snackbar: SnackbarHostState,
    onOpenTask: (TaskId) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTrash: () -> Unit,
    viewModel: TaskTreeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = state.day.date
    val tagsById = state.tags.associateBy { it.id }
    var overlay by rememberSaveable { mutableStateOf(TreeOverlay.NONE) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDay()
        onPauseOrDispose {}
    }
    UndoSnackbars(viewModel.undoable, snackbar, viewModel::undo)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = stringResource(R.string.tasks_title)) {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.tasks_search))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.task_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tasks_templates)) },
                            onClick = {
                                menuOpen = false
                                overlay = TreeOverlay.TEMPLATES
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tasks_show_finished)) },
                            trailingIcon = { Checkbox(checked = state.showFinished, onCheckedChange = null) },
                            onClick = {
                                menuOpen = false
                                viewModel.setShowFinished(!state.showFinished)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tasks_trash)) },
                            onClick = {
                                menuOpen = false
                                onOpenTrash()
                            },
                        )
                    }
                }
            }

            if (state.tags.isNotEmpty()) {
                TagFilterRow(tags = state.tags, selected = state.tagFilter, onSelect = viewModel::setTagFilter)
            }

            if (state.loaded && state.rows.isEmpty()) {
                EmptyTree()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    /** Room at the bottom so the last row can scroll clear of the add button. */
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(state.rows, key = { it.task.id.value }) { node ->
                        TreeRow(
                            node = node,
                            state = state,
                            tagsById = tagsById,
                            onToggleDone = { if (node.task.isOpen) viewModel.complete(node.task) else viewModel.reopen(node.task.id) },
                            onTomorrow = { viewModel.moveTo(node.task, today.plusDays(1), countsAsCarry = true) },
                            onOpen = { onOpenTask(node.task.id) },
                            onToggleExpanded = { viewModel.toggleExpanded(node.task.id) },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { overlay = TreeOverlay.QUICK_ADD },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(ScreenPadding),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.quick_add_open))
        }
    }

    when (overlay) {
        TreeOverlay.NONE -> Unit
        TreeOverlay.QUICK_ADD -> QuickAddSheet(parentId = null, defaultDate = today, onDismiss = { overlay = TreeOverlay.NONE })
        TreeOverlay.TEMPLATES -> TemplateSheet(onDismiss = { overlay = TreeOverlay.NONE })
    }
}

@Composable
private fun TreeRow(
    node: TaskNode,
    state: TaskTreeUiState,
    tagsById: Map<TagId, Tag>,
    onToggleDone: () -> Unit,
    onTomorrow: () -> Unit,
    onOpen: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val expandArrow: @Composable () -> Unit = {
        IconButton(onClick = onToggleExpanded) {
            Icon(
                if (node.expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(
                    if (node.expanded) R.string.tasks_collapse else R.string.tasks_expand,
                    node.task.name,
                ),
            )
        }
    }
    val row: @Composable () -> Unit = {
        TaskRow(
            task = node.task,
            today = state.day.date,
            tags = tagsById,
            progress = state.progress[node.task.id],
            depth = node.depth,
            onToggleDone = onToggleDone,
            onClick = onOpen,
            trailing = if (node.hasChildren) expandArrow else null,
        )
    }
    /** Only an open task can be swiped; a finished one has nothing left to finish or postpone. */
    if (node.task.isOpen) {
        SwipeActions(
            rightLabel = stringResource(R.string.task_swipe_done),
            onSwipeRight = onToggleDone,
            leftLabel = stringResource(R.string.task_swipe_tomorrow),
            onSwipeLeft = onTomorrow,
            content = row,
        )
    } else {
        row()
    }
}

@Composable
private fun TagFilterRow(tags: List<Tag>, selected: TagId?, onSelect: (TagId?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "all") {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.tasks_all_tags)) })
        }
        items(tags, key = { it.id.value }) { tag ->
            FilterChip(
                selected = selected == tag.id,
                /** Tapping the chosen tag again goes back to all tasks. */
                onClick = { onSelect(if (selected == tag.id) null else tag.id) },
                label = { Text(tag.name) },
                leadingIcon = { TagDot(tag.colorIndex) },
            )
        }
    }
}

@Composable
private fun EmptyTree() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.tasks_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.tasks_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
