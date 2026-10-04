package com.karan.anuj.core.data.task

import com.karan.anuj.core.data.db.AttachmentDao
import com.karan.anuj.core.data.db.ChecklistDao
import com.karan.anuj.core.data.db.NoteDao
import com.karan.anuj.core.data.db.TagDao
import com.karan.anuj.core.data.di.IoDispatcher
import com.karan.anuj.core.domain.task.Attachment
import com.karan.anuj.core.domain.task.AttachmentRepository
import com.karan.anuj.core.domain.task.ChecklistItem
import com.karan.anuj.core.domain.task.ChecklistRepository
import com.karan.anuj.core.domain.task.Note
import com.karan.anuj.core.domain.task.NoteRepository
import com.karan.anuj.core.domain.task.Tag
import com.karan.anuj.core.domain.task.TagRepository
import com.karan.anuj.core.domain.task.TaskId
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** SQLite limits how many values one statement may carry, so id lists are sent in pieces. */
private const val CHUNK = 400

class RoomChecklistRepository @Inject constructor(
    private val dao: Lazy<ChecklistDao>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ChecklistRepository {

    override fun observeFor(taskId: TaskId): Flow<List<ChecklistItem>> =
        flow { emitAll(dao.get().observeFor(taskId.value).map { rows -> rows.map { it.toDomain() } }) }.flowOn(io)

    override suspend fun getFor(taskIds: List<TaskId>): List<ChecklistItem> = withContext(io) {
        taskIds.map { it.value }.chunked(CHUNK).flatMap { dao.get().getFor(it) }.map { it.toDomain() }
    }

    override suspend fun save(items: List<ChecklistItem>) = withContext(io) {
        dao.get().upsert(items.map { it.toEntity() })
    }
}

class RoomNoteRepository @Inject constructor(
    private val dao: Lazy<NoteDao>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : NoteRepository {

    override fun observeFor(taskId: TaskId): Flow<List<Note>> =
        flow { emitAll(dao.get().observeFor(taskId.value).map { rows -> rows.map { it.toDomain() } }) }.flowOn(io)

    override suspend fun save(note: Note) = withContext(io) { dao.get().upsert(note.toEntity()) }
}

class RoomTagRepository @Inject constructor(
    private val dao: Lazy<TagDao>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : TagRepository {

    override fun observeAll(): Flow<List<Tag>> =
        flow { emitAll(dao.get().observeAll().map { rows -> rows.map { it.toDomain() } }) }.flowOn(io)

    override suspend fun getAll(): List<Tag> = withContext(io) { dao.get().getAll().map { it.toDomain() } }

    override suspend fun save(tags: List<Tag>) = withContext(io) { dao.get().upsert(tags.map { it.toEntity() }) }
}

class RoomAttachmentRepository @Inject constructor(
    private val dao: Lazy<AttachmentDao>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AttachmentRepository {

    override fun observeFor(taskId: TaskId): Flow<List<Attachment>> =
        flow { emitAll(dao.get().observeFor(taskId.value).map { rows -> rows.map { it.toDomain() } }) }.flowOn(io)

    override suspend fun getFor(taskIds: List<TaskId>): List<Attachment> = withContext(io) {
        taskIds.map { it.value }.chunked(CHUNK).flatMap { dao.get().getFor(it) }.map { it.toDomain() }
    }

    override suspend fun save(attachment: Attachment) = withContext(io) { dao.get().upsert(attachment.toEntity()) }
}
