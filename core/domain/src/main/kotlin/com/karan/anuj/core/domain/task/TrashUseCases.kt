package com.karan.anuj.core.domain.task

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DeleteTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val editor: TaskEditor,
) {
    /**
     * Moves a task and everything beneath it to the trash. They all get the
     * same deletion time, which is how a later restore knows which sub-tasks
     * went with it and which had already been deleted separately.
     */
    suspend operator fun invoke(id: TaskId) {
        val now = editor.now()
        val going = tasks.getSubtree(id).filterNot { it.stamps.isDeleted }
        editor.apply(going.map { it to it.copy(stamps = it.stamps.deleted(now)) }, now)
    }
}

class RestoreTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val editor: TaskEditor,
) {
    /**
     * Brings a task back with the sub-tasks that were deleted along with it.
     * If its parent is gone or still in the trash, it comes back as a
     * top-level task instead of being attached to something invisible.
     */
    suspend operator fun invoke(id: TaskId) {
        val task = tasks.get(id) ?: return
        val deletedAt = task.stamps.deletedAt ?: return
        val now = editor.now()

        val parent = task.parentId?.let { tasks.get(it) }
        val parentUsable = parent != null && !parent.stamps.isDeleted

        val changes = tasks.getSubtree(id)
            .filter { it.stamps.deletedAt == deletedAt }
            .map { deleted ->
                val restored = deleted.copy(stamps = deleted.stamps.restored(now))
                deleted to if (deleted.id == id && !parentUsable) restored.copy(parentId = null) else restored
            }
        editor.apply(changes, now)
    }
}

class PurgeTasksUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val attachments: AttachmentRepository,
    private val files: AttachmentFileStore,
) {
    /** Removes tasks for good, with everything beneath them and their photo files. This cannot be undone. */
    suspend operator fun invoke(ids: List<TaskId>) {
        val all = ids.flatMap { tasks.getSubtree(it) }.map { it.id }.distinct()
        if (all.isEmpty()) return
        val photoFiles = attachments.getFor(all).map { it.fileName }
        tasks.purge(all)
        files.delete(photoFiles)
    }
}

class EmptyTrashUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val purge: PurgeTasksUseCase,
) {
    suspend operator fun invoke() = purge(tasks.getTrash().map { it.id })
}

class ObserveTrashUseCase @Inject constructor(
    private val tasks: TaskRepository,
) {
    /**
     * The trash as the user thinks of it: one entry per thing they deleted.
     * Sub-tasks that went to the trash together with their parent are folded
     * into the parent's entry.
     */
    operator fun invoke(): Flow<List<Task>> = tasks.observeTrash().map { trashed ->
        val byId = trashed.associateBy { it.id }
        trashed
            .filter { task ->
                val parent = task.parentId?.let(byId::get)
                parent == null || parent.stamps.deletedAt != task.stamps.deletedAt
            }
            .sortedByDescending { it.stamps.deletedAt }
    }
}
