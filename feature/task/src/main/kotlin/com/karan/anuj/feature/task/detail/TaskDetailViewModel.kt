package com.karan.anuj.feature.task.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.task.Attachment
import com.karan.anuj.core.domain.task.AttachmentActions
import com.karan.anuj.core.domain.task.ChecklistActions
import com.karan.anuj.core.domain.task.ChecklistItem
import com.karan.anuj.core.domain.task.DuplicateTaskUseCase
import com.karan.anuj.core.domain.task.Note
import com.karan.anuj.core.domain.task.NoteActions
import com.karan.anuj.core.domain.task.ObserveTaskDetailUseCase
import com.karan.anuj.core.domain.task.Repetition
import com.karan.anuj.core.domain.task.Tag
import com.karan.anuj.core.domain.task.TagActions
import com.karan.anuj.core.domain.task.TagId
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskDetail
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.UpdateTaskUseCase
import com.karan.anuj.feature.task.common.Day
import com.karan.anuj.feature.task.common.DayClock
import com.karan.anuj.feature.task.common.TaskActions
import com.karan.anuj.feature.task.common.TaskActionsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

sealed interface TaskDetailUiState {
    data object Loading : TaskDetailUiState

    /** The task was removed for good, or is in the trash. */
    data object Gone : TaskDetailUiState

    data class Loaded(val detail: TaskDetail, val day: Day) : TaskDetailUiState
}

/** A checklist line, note or photo just removed from the task, and how to bring it back. */
class RemovedPart(val label: String, val restore: suspend () -> Unit)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    observeDetail: ObserveTaskDetailUseCase,
    private val updateTask: UpdateTaskUseCase,
    private val checklist: ChecklistActions,
    private val notes: NoteActions,
    private val tags: TagActions,
    private val photos: AttachmentActions,
    private val duplicateTask: DuplicateTaskUseCase,
    private val actions: TaskActions,
) : TaskActionsViewModel(actions) {

    private val taskId = TaskId(checkNotNull(savedState.get<String>(TASK_ID_ARG)) { "Task screen opened without a task id" })
    private val clock = DayClock(viewModelScope)

    val state: StateFlow<TaskDetailUiState> =
        combine(observeDetail(taskId), clock.day) { detail, day ->
            if (detail == null || detail.task.stamps.isDeleted) TaskDetailUiState.Gone
            else TaskDetailUiState.Loaded(detail, day)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState.Loading)

    private val removedChannel = Channel<RemovedPart>(Channel.BUFFERED)
    val removedParts: Flow<RemovedPart> = removedChannel.receiveAsFlow()

    fun refreshDay() = clock.refresh()

    fun update(change: (Task) -> Task) {
        actions.writes.launch { updateTask(taskId, change) }
    }

    /** A rhythm needs a first day, so a task given a repeat rule while it has no date starts today. */
    fun setRepetition(rule: Repetition?, daysOff: Set<DayOfWeek>) = update {
        it.copy(
            repetition = rule,
            daysOff = daysOff,
            dueDate = it.dueDate ?: clock.day.value.date.takeIf { rule != null },
        )
    }

    fun toggleTag(tag: TagId) = update {
        it.copy(tagIds = if (tag in it.tagIds) it.tagIds - tag else it.tagIds + tag)
    }

    fun createTag(name: String, colorIndex: Int) {
        actions.writes.launch {
            val tag = tags.create(name, colorIndex) ?: return@launch
            updateTask(taskId) { it.copy(tagIds = it.tagIds + tag.id) }
        }
    }

    fun removeTag(tag: Tag) {
        actions.writes.launch { tags.remove(tag) }
    }

    fun addChecklistLine(text: String) {
        actions.writes.launch { checklist.add(taskId, text) }
    }

    fun setChecked(item: ChecklistItem, checked: Boolean) {
        actions.writes.launch { checklist.setChecked(item, checked) }
    }

    fun removeChecklistLine(item: ChecklistItem) {
        actions.writes.launch {
            checklist.remove(item)
            removedChannel.send(RemovedPart(item.text) { checklist.restore(item) })
        }
    }

    fun addNote(text: String) {
        actions.writes.launch { notes.add(taskId, text) }
    }

    fun removeNote(note: Note) {
        actions.writes.launch {
            notes.remove(note)
            removedChannel.send(RemovedPart(note.text.take(REMOVED_LABEL_LENGTH)) { notes.restore(note) })
        }
    }

    fun addPickedPhoto(sourceUri: String) {
        actions.writes.launch { photos.addFromPicker(taskId, sourceUri) }
    }

    /**
     * Makes the file the camera will write into and hands back its address.
     * The file's name is kept in saved state: the camera app can push this
     * app out of memory, and the result must still find its file afterwards.
     */
    fun prepareCamera(onReady: (uri: String) -> Unit) {
        actions.writes.launch {
            val (fileName, uri) = photos.prepareCameraFile()
            savedState[PENDING_PHOTO] = fileName
            onReady(uri)
        }
    }

    fun onCameraResult(photoTaken: Boolean) {
        val fileName = savedState.get<String>(PENDING_PHOTO) ?: return
        savedState[PENDING_PHOTO] = null
        actions.writes.launch { photos.finishCamera(taskId, fileName, photoTaken) }
    }

    fun removePhoto(photo: Attachment, label: String) {
        actions.writes.launch {
            photos.remove(photo)
            removedChannel.send(RemovedPart(label) { photos.restore(photo) })
        }
    }

    fun photoPath(photo: Attachment): String = photos.pathOf(photo)

    fun restore(part: RemovedPart) {
        actions.writes.launch { part.restore() }
    }

    /**
     * Moves the task to the trash and only then reports back. The screen
     * closes on [onDone], and closing it ends this view model's work, so
     * leaving first would cancel the delete before it was saved.
     */
    fun moveToTrash(task: Task, onDone: () -> Unit) {
        actions.writes.launch {
            actions.delete(task)
            onDone()
        }
    }

    fun duplicate(nameOfCopy: (String) -> String, onCopied: (TaskId) -> Unit) {
        actions.writes.launch { duplicateTask(taskId, nameOfCopy)?.let(onCopied) }
    }

    companion object {
        /** The name of the navigation argument carrying the task's id. */
        const val TASK_ID_ARG = "taskId"
        private const val PENDING_PHOTO = "pendingPhoto"
        private const val REMOVED_LABEL_LENGTH = 30
    }
}
