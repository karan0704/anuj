package com.karan.anuj.feature.task.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import com.karan.anuj.core.domain.task.CompleteTaskUseCase
import com.karan.anuj.core.domain.task.CompletionUndo
import com.karan.anuj.core.domain.task.DeleteTaskUseCase
import com.karan.anuj.core.domain.task.MoveTaskToDayUseCase
import com.karan.anuj.core.domain.task.ReopenTaskUseCase
import com.karan.anuj.core.domain.task.RestoreTaskUseCase
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.UndoCompletionUseCase
import com.karan.anuj.core.domain.task.UpdateTaskUseCase
import com.karan.anuj.feature.task.R
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Something just done to a task that the user can still take back. */
sealed interface TaskUndo {
    val taskName: String

    data class Completed(override val taskName: String, val undo: CompletionUndo) : TaskUndo
    data class Deleted(override val taskName: String, val id: TaskId) : TaskUndo
    data class Moved(
        override val taskName: String,
        val id: TaskId,
        val previousDate: LocalDate?,
        val previousCarryCount: Int,
    ) : TaskUndo
}

/**
 * The quick actions every task list offers (done, trash, move to another
 * day) and how each is undone. One class so Today, the tree, the inbox and
 * search all behave the same.
 */
class TaskActions @Inject constructor(
    private val completeTask: CompleteTaskUseCase,
    private val undoCompletion: UndoCompletionUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val restoreTask: RestoreTaskUseCase,
    private val moveTask: MoveTaskToDayUseCase,
    private val reopenTask: ReopenTaskUseCase,
    private val updateTask: UpdateTaskUseCase,
    val writes: WriteScope,
) {
    suspend fun complete(task: Task): TaskUndo? =
        completeTask(task.id, Day.now().date)?.let { TaskUndo.Completed(task.name, it) }

    suspend fun delete(task: Task): TaskUndo {
        deleteTask(task.id)
        return TaskUndo.Deleted(task.name, task.id)
    }

    suspend fun moveTo(task: Task, date: LocalDate?, countsAsCarry: Boolean): TaskUndo {
        moveTask(task.id, date, countsAsCarry)
        return TaskUndo.Moved(task.name, task.id, task.dueDate, task.carryCount)
    }

    suspend fun reopen(id: TaskId) = reopenTask(id)

    suspend fun undo(action: TaskUndo) = when (action) {
        is TaskUndo.Completed -> undoCompletion(action.undo)
        is TaskUndo.Deleted -> restoreTask(action.id)
        is TaskUndo.Moved -> updateTask(action.id) {
            it.copy(dueDate = action.previousDate, carryCount = action.previousCarryCount)
        }
    }
}

/**
 * Base for the list screens' view models: runs the quick actions and hands
 * each result to the screen so it can offer "Undo".
 */
abstract class TaskActionsViewModel(private val actions: TaskActions) : ViewModel() {

    private val undoChannel = Channel<TaskUndo>(Channel.BUFFERED)
    val undoable: Flow<TaskUndo> = undoChannel.receiveAsFlow()

    fun complete(task: Task) = act { actions.complete(task) }

    fun delete(task: Task) = act { actions.delete(task) }

    fun moveTo(task: Task, date: LocalDate?, countsAsCarry: Boolean) = act { actions.moveTo(task, date, countsAsCarry) }

    fun reopen(id: TaskId) = act {
        actions.reopen(id)
        null
    }

    fun undo(action: TaskUndo) = act {
        actions.undo(action)
        null
    }

    /**
     * Called once an action has been saved. Screens whose list follows the
     * database by itself need nothing here; search, which reads once per
     * query, uses it to read again.
     */
    protected open fun onSaved() = Unit

    private fun act(action: suspend () -> TaskUndo?) {
        actions.writes.launch {
            val undo = action()
            onSaved()
            undo?.let { undoChannel.send(it) }
        }
    }
}

/**
 * Shows a short message with an "Undo" button for each action coming from
 * [undoable]. A new action replaces the message of the previous one, so a
 * run of quick ticks does not queue up a row of messages.
 */
@Composable
fun UndoSnackbars(
    undoable: Flow<TaskUndo>,
    snackbar: SnackbarHostState,
    onUndo: (TaskUndo) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(undoable, snackbar) {
        undoable.collect { action ->
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val message = context.getString(
                    when (action) {
                        is TaskUndo.Completed -> R.string.task_done_message
                        is TaskUndo.Deleted -> R.string.task_deleted_message
                        is TaskUndo.Moved -> R.string.task_moved_message
                    },
                    action.taskName,
                )
                val result = snackbar.showSnackbar(
                    message = message,
                    actionLabel = context.getString(R.string.task_undo),
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) onUndo(action)
            }
        }
    }
}
