package com.karan.anuj.core.data.history

import com.karan.anuj.core.data.db.ChangeHistoryDao
import com.karan.anuj.core.data.db.ChangeHistoryEntity
import com.karan.anuj.core.data.di.IoDispatcher
import com.karan.anuj.core.domain.history.ChangeHistoryRepository
import com.karan.anuj.core.domain.history.RecordChange
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The DAO is taken lazily and only ever touched on the IO dispatcher: asking
 * for it the first time opens the encrypted database, which reads the key
 * from the keystore and must not happen on the main thread.
 */
class RoomChangeHistoryRepository @Inject constructor(
    private val dao: Lazy<ChangeHistoryDao>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ChangeHistoryRepository {

    override suspend fun record(changes: List<RecordChange>) = withContext(io) {
        dao.get().insertAll(changes.map { it.toEntity() })
    }

    override fun observeFor(table: String, rowId: String): Flow<List<RecordChange>> =
        flow {
            emitAll(dao.get().observeFor(table, rowId).map { rows -> rows.map { it.toDomain() } })
        }.flowOn(io)

    override fun observeRecent(limit: Int): Flow<List<RecordChange>> =
        flow { emitAll(dao.get().observeRecent(limit).map { rows -> rows.map { it.toDomain() } }) }.flowOn(io)

    override suspend fun deleteBefore(millis: Long) = withContext(io) { dao.get().deleteBefore(millis) }

    private fun RecordChange.toEntity() = ChangeHistoryEntity(
        tableName = table,
        rowId = rowId,
        field = field,
        oldValue = oldValue,
        newValue = newValue,
        changedAt = changedAt,
    )

    private fun ChangeHistoryEntity.toDomain() = RecordChange(
        table = tableName,
        rowId = rowId,
        field = field,
        oldValue = oldValue,
        newValue = newValue,
        changedAt = changedAt,
    )
}
