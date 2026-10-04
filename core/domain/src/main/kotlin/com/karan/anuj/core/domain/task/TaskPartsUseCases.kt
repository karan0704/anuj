package com.karan.anuj.core.domain.task

import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.time.TimeSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class NoteActions @Inject constructor(
    private val notes: NoteRepository,
    private val ids: IdGenerator,
    private val time: TimeSource,
) {
    suspend fun add(taskId: TaskId, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        notes.save(Note(ids.newId(), taskId, trimmed, RecordStamps.created(time.nowMillis())))
    }

    suspend fun remove(note: Note) {
        notes.save(note.copy(stamps = note.stamps.deleted(time.nowMillis())))
    }

    suspend fun restore(note: Note) {
        notes.save(note.copy(stamps = note.stamps.restored(time.nowMillis())))
    }
}

class TagActions @Inject constructor(
    private val tags: TagRepository,
    private val ids: IdGenerator,
    private val time: TimeSource,
) {
    fun observe(): Flow<List<Tag>> = tags.observeAll()

    /** @return the new tag, the existing one if a tag with this name is already there, or null for a blank name */
    suspend fun create(name: String, colorIndex: Int): Tag? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        tags.getAll().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }

        val tag = Tag(TagId(ids.newId()), trimmed, colorIndex, RecordStamps.created(time.nowMillis()))
        tags.save(listOf(tag))
        return tag
    }

    suspend fun remove(tag: Tag) {
        tags.save(listOf(tag.copy(stamps = tag.stamps.deleted(time.nowMillis()))))
    }

    /**
     * Gives a new install a few tags to pick from, so tagging a task works
     * by tapping from the first day. Does nothing once any tag exists.
     */
    suspend fun ensureStarterTags(names: List<String>) {
        if (tags.getAll().isNotEmpty()) return
        val now = time.nowMillis()
        tags.save(names.mapIndexed { index, name -> Tag(TagId(ids.newId()), name, index, RecordStamps.created(now)) })
    }
}

class AttachmentActions @Inject constructor(
    private val attachments: AttachmentRepository,
    private val files: AttachmentFileStore,
    private val ids: IdGenerator,
    private val time: TimeSource,
) {
    /** Copies a picked picture into the app and attaches it to the task. */
    suspend fun addFromPicker(taskId: TaskId, sourceUri: String) {
        attach(taskId, files.importFrom(sourceUri))
    }

    /**
     * Step one of taking a photo: makes the empty file the camera will fill.
     * @return the file's name and the address to hand to the camera
     */
    suspend fun prepareCameraFile(): Pair<String, String> {
        val fileName = files.reserveForCamera()
        return fileName to files.shareableUriFor(fileName)
    }

    /** Step two: the camera saved a photo into [fileName], so attach it; or it was cancelled, so discard the file. */
    suspend fun finishCamera(taskId: TaskId, fileName: String, photoTaken: Boolean) {
        if (photoTaken) attach(taskId, fileName) else files.delete(listOf(fileName))
    }

    /** The file stays on disk until the task is removed for good, so this can be undone. */
    suspend fun remove(attachment: Attachment) {
        attachments.save(attachment.copy(stamps = attachment.stamps.deleted(time.nowMillis())))
    }

    suspend fun restore(attachment: Attachment) {
        attachments.save(attachment.copy(stamps = attachment.stamps.restored(time.nowMillis())))
    }

    fun pathOf(attachment: Attachment): String = files.pathOf(attachment.fileName)

    private suspend fun attach(taskId: TaskId, fileName: String) {
        attachments.save(Attachment(ids.newId(), taskId, fileName, RecordStamps.created(time.nowMillis())))
    }
}

class TaskPreferencesUseCase @Inject constructor(
    private val repository: TaskPreferencesRepository,
) {
    fun observe(): Flow<TaskPreferences> = repository.preferences

    suspend fun setDefaultCarryOver(rule: CarryOverRule) = repository.setDefaultCarryOver(rule)

    suspend fun setCarryLimit(limit: Int) = repository.setCarryLimit(limit.coerceIn(1, 99))

    suspend fun setDayParts(parts: DayParts) = repository.setDayParts(parts)

    /** Kept shortest first and without repeats; an empty list is refused so the chips never vanish. */
    suspend fun setEstimateChoices(minutes: List<Int>) {
        val cleaned = minutes.filter { it > 0 }.distinct().sorted()
        if (cleaned.isNotEmpty()) repository.setEstimateChoices(cleaned)
    }
}
