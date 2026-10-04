package com.karan.anuj.core.data.task

import com.karan.anuj.core.data.db.TaskDao
import com.karan.anuj.core.data.db.TaskEntity
import com.karan.anuj.core.data.db.TaskTagEntity
import com.karan.anuj.core.data.di.IoDispatcher
import com.karan.anuj.core.domain.task.ChildProgress
import com.karan.anuj.core.domain.task.SearchQuery
import com.karan.anuj.core.domain.task.TagId
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TaskOccurrence
import com.karan.anuj.core.domain.task.TaskRepository
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The DAO is taken lazily and only touched on the IO dispatcher, for the
 * same reason as in the change-history repository: the first use opens the
 * encrypted database.
 */
class RoomTaskRepository @Inject constructor(
    private val dao: Lazy<TaskDao>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : TaskRepository {

    override fun observeOpen() = withTags { it.observeOpen() }

    override fun observeAll() = withTags { it.observeAll() }

    override fun observeCompletedBetween(fromMillis: Long, toMillis: Long) =
        withTags { it.observeCompletedBetween(fromMillis, toMillis) }

    override fun observeChildren(parentId: TaskId) = withTags { it.observeChildren(parentId.value) }

    override fun observeTrash() = withTags { it.observeTrash() }

    override fun observeTask(id: TaskId): Flow<Task?> = onIo { dao ->
        combine(dao.observeById(id.value), dao.observeTags()) { row, tags ->
            row?.toDomain(tags.grouped()[row.id].orEmpty())
        }
    }

    override fun observeChildProgress(): Flow<Map<TaskId, ChildProgress>> = onIo { dao ->
        dao.observeChildProgress().map { rows ->
            rows.associate { TaskId(it.parentId) to ChildProgress(total = it.total, done = it.done) }
        }
    }

    override fun observeOccurrencesBetween(fromMillis: Long, toMillis: Long): Flow<List<TaskOccurrence>> = onIo { dao ->
        dao.observeOccurrencesBetween(fromMillis, toMillis).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun get(id: TaskId): Task? = withContext(io) {
        dao.get().getById(id.value)?.let { listOf(it).attachTags().single() }
    }

    override suspend fun getOpen(): List<Task> = withContext(io) { dao.get().getOpen().attachTags() }

    override suspend fun getSubtree(id: TaskId): List<Task> = withContext(io) {
        dao.get().getSubtree(id.value).attachTags()
    }

    override suspend fun getTrash(): List<Task> = withContext(io) { dao.get().getTrash().attachTags() }

    override suspend fun save(tasks: List<Task>) = withContext(io) {
        if (tasks.isEmpty()) return@withContext
        /** Chunked because SQLite limits how many values one statement may carry. */
        tasks.chunked(CHUNK).forEach { chunk ->
            dao.get().save(chunk.map { it.toEntity() }, chunk.flatMap { it.toTagRows() })
        }
    }

    override suspend fun purge(ids: List<TaskId>) = withContext(io) {
        ids.map { it.value }.chunked(CHUNK).forEach { dao.get().deleteByIds(it) }
    }

    override suspend fun search(query: SearchQuery): List<Task> = withContext(io) {
        dao.get().search(query.matchExpression, SEARCH_LIMIT).attachTags()
    }

    override suspend fun recentNames(limit: Int): List<String> = withContext(io) { dao.get().recentNames(limit) }

    override suspend fun addOccurrence(occurrence: TaskOccurrence) = withContext(io) {
        dao.get().insertOccurrence(occurrence.toEntity())
    }

    override suspend fun removeOccurrence(id: String) = withContext(io) { dao.get().deleteOccurrence(id) }

    private fun <T> onIo(block: (TaskDao) -> Flow<T>): Flow<T> = flow { emitAll(block(dao.get())) }.flowOn(io)

    /** A stream of task rows joined with the stream of tag rows, so a tag change also refreshes the list. */
    private fun withTags(rows: (TaskDao) -> Flow<List<TaskEntity>>): Flow<List<Task>> = onIo { dao ->
        combine(rows(dao), dao.observeTags()) { tasks, tags ->
            val byTask = tags.grouped()
            tasks.map { it.toDomain(byTask[it.id].orEmpty()) }
        }
    }

    private suspend fun List<TaskEntity>.attachTags(): List<Task> {
        if (isEmpty()) return emptyList()
        val byTask = map { it.id }.chunked(CHUNK).flatMap { dao.get().getTagsFor(it) }.grouped()
        return map { it.toDomain(byTask[it.id].orEmpty()) }
    }

    private fun List<TaskTagEntity>.grouped(): Map<String, Set<TagId>> =
        groupBy({ it.taskId }, { TagId(it.tagId) }).mapValues { it.value.toSet() }

    private companion object {
        const val CHUNK = 400
        const val SEARCH_LIMIT = 100
    }
}
