package com.karan.anuj.core.data.place

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karan.anuj.core.data.db.PlaceDao
import com.karan.anuj.core.data.db.PlaceEntity
import com.karan.anuj.core.data.db.PlaceVisitEntity
import com.karan.anuj.core.data.db.StampColumns
import com.karan.anuj.core.data.db.TaskPlaceEntity
import com.karan.anuj.core.data.di.IoDispatcher
import com.karan.anuj.core.domain.place.LocationFix
import com.karan.anuj.core.domain.place.Place
import com.karan.anuj.core.domain.place.PlaceId
import com.karan.anuj.core.domain.place.PlaceKind
import com.karan.anuj.core.domain.place.PlaceMoment
import com.karan.anuj.core.domain.place.PlaceRepository
import com.karan.anuj.core.domain.place.PlaceSettings
import com.karan.anuj.core.domain.place.PlaceSettingsRepository
import com.karan.anuj.core.domain.place.PlaceVisit
import com.karan.anuj.core.domain.place.TaskPlace
import com.karan.anuj.core.domain.place.TrackerState
import com.karan.anuj.core.domain.task.TaskId
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A kind saved by a newer version of the app is treated as a spot that sets nothing off. */
private fun PlaceEntity.toDomain() = Place(
    id = PlaceId(id),
    name = name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    kind = PlaceKind.entries.firstOrNull { it.name == kind } ?: PlaceKind.VISITED,
    isHome = isHome,
    stamps = stamps.toDomain(),
)

private fun Place.toEntity() =
    PlaceEntity(id.value, name, latitude, longitude, radiusMeters, kind.name, isHome, StampColumns.from(stamps))

private fun PlaceVisitEntity.toDomain() = PlaceVisit(id, PlaceId(placeId), arrivedAt, leftAt)

private fun TaskPlaceEntity.toDomain(): TaskPlace? {
    val moment = PlaceMoment.entries.firstOrNull { it.name == moment } ?: return null
    return TaskPlace(TaskId(taskId), PlaceId(placeId), moment)
}

/** The DAO is taken lazily and only touched on the IO dispatcher, because the first use opens the encrypted database. */
class RoomPlaceRepository @Inject constructor(
    private val dao: Lazy<PlaceDao>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PlaceRepository {

    private fun <T> onIo(query: (PlaceDao) -> Flow<T>): Flow<T> = flow { emitAll(query(dao.get())) }.flowOn(io)

    override fun observePlaces(): Flow<List<Place>> = onIo { dao -> dao.observeShown().map { rows -> rows.map { it.toDomain() } } }

    override suspend fun getPlaces(): List<Place> = withContext(io) { dao.get().getAll().map { it.toDomain() } }

    override suspend fun get(id: PlaceId): Place? = withContext(io) { dao.get().get(id.value)?.toDomain() }

    override suspend fun save(place: Place) = withContext(io) { dao.get().upsert(place.toEntity()) }

    override suspend fun remove(id: PlaceId) = withContext(io) { dao.get().delete(id.value) }

    override suspend fun saveVisit(visit: PlaceVisit) = withContext(io) {
        dao.get().upsertVisit(PlaceVisitEntity(visit.id, visit.placeId.value, visit.arrivedAt, visit.leftAt))
    }

    override suspend fun openVisit(placeId: PlaceId): PlaceVisit? = withContext(io) { dao.get().openVisit(placeId.value)?.toDomain() }

    override suspend fun countVisits(placeId: PlaceId): Int = withContext(io) { dao.get().countVisits(placeId.value) }

    override suspend fun deleteVisitsBefore(millis: Long) = withContext(io) { dao.get().deleteVisitsBefore(millis) }

    override fun observeTaskPlace(taskId: TaskId): Flow<TaskPlace?> = onIo { dao -> dao.observeTie(taskId.value).map { it?.toDomain() } }

    override suspend fun setTaskPlace(taskId: TaskId, tie: TaskPlace?) = withContext(io) {
        if (tie == null) dao.get().deleteTie(taskId.value)
        else dao.get().upsertTie(TaskPlaceEntity(taskId.value, tie.placeId.value, tie.moment.name))
    }

    override suspend fun tiesOf(placeId: PlaceId): List<TaskPlace> =
        withContext(io) { dao.get().tiesOf(placeId.value).mapNotNull { it.toDomain() } }
}

private val Context.placeStore: DataStore<Preferences> by preferencesDataStore(name = "place_settings")

/**
 * The place settings, and what the tracker remembers between two readings.
 * The tracker's memory is a handful of values rewritten with every reading,
 * so it lives here and not in the database.
 */
@Singleton
class DataStorePlaceSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaceSettingsRepository {

    private val defaults = PlaceSettings()

    override val settings: Flow<PlaceSettings> = context.placeStore.data
        .map { prefs ->
            PlaceSettings(
                enabled = prefs[ENABLED] ?: defaults.enabled,
                radiusMeters = prefs[RADIUS] ?: defaults.radiusMeters,
                stayMinutes = prefs[STAY] ?: defaults.stayMinutes,
                noticeAfterMinutes = prefs[NOTICE_MINUTES] ?: defaults.noticeAfterMinutes,
                noticeAfterVisits = prefs[NOTICE_VISITS] ?: defaults.noticeAfterVisits,
                arriveAfterMinutes = prefs[ARRIVE] ?: defaults.arriveAfterMinutes,
                settleMinutes = prefs[SETTLE] ?: defaults.settleMinutes,
                askAboutNewPlaces = prefs[ASK] ?: defaults.askAboutNewPlaces,
            )
        }
        .distinctUntilChanged()

    override suspend fun update(change: (PlaceSettings) -> PlaceSettings) {
        val next = change(settings.first())
        context.placeStore.edit { prefs ->
            prefs[ENABLED] = next.enabled
            prefs[RADIUS] = next.radiusMeters
            prefs[STAY] = next.stayMinutes
            prefs[NOTICE_MINUTES] = next.noticeAfterMinutes
            prefs[NOTICE_VISITS] = next.noticeAfterVisits
            prefs[ARRIVE] = next.arriveAfterMinutes
            prefs[SETTLE] = next.settleMinutes
            prefs[ASK] = next.askAboutNewPlaces
        }
    }

    override suspend fun trackerState(): TrackerState = decode(context.placeStore.data.first()[TRACKER])

    override suspend fun setTrackerState(state: TrackerState) {
        context.placeStore.edit { it[TRACKER] = encode(state) }
    }

    /** placeId;since;arrived;settled;anchor latitude;anchor longitude;anchor accuracy;anchor time. Empty parts mean "none". */
    private fun encode(state: TrackerState): String = listOf(
        state.placeId?.value.orEmpty(),
        state.since,
        state.arrived,
        state.settled,
        state.anchor?.latitude ?: "",
        state.anchor?.longitude ?: "",
        state.anchor?.accuracyMeters ?: "",
        state.anchor?.at ?: "",
    ).joinToString(SEPARATOR)

    /** Anything that does not read back cleanly starts the tracker afresh, which costs one arrival delay and nothing else. */
    private fun decode(stored: String?): TrackerState {
        val parts = stored?.split(SEPARATOR) ?: return TrackerState()
        if (parts.size != TRACKER_PARTS) return TrackerState()
        val anchor = parts[4].toDoubleOrNull()?.let { latitude ->
            LocationFix(latitude, parts[5].toDoubleOrNull() ?: return TrackerState(), parts[6].toFloatOrNull() ?: 0f, parts[7].toLongOrNull() ?: 0)
        }
        return TrackerState(
            placeId = parts[0].takeIf { it.isNotEmpty() }?.let(::PlaceId),
            since = parts[1].toLongOrNull() ?: 0,
            arrived = parts[2].toBoolean(),
            settled = parts[3].toBoolean(),
            anchor = anchor,
        )
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val RADIUS = intPreferencesKey("radius_meters")
        val STAY = intPreferencesKey("stay_minutes")
        val NOTICE_MINUTES = intPreferencesKey("notice_after_minutes")
        val NOTICE_VISITS = intPreferencesKey("notice_after_visits")
        val ARRIVE = intPreferencesKey("arrive_after_minutes")
        val SETTLE = intPreferencesKey("settle_minutes")
        val ASK = booleanPreferencesKey("ask_about_new_places")
        val TRACKER = stringPreferencesKey("tracker_state")
        const val SEPARATOR = ";"
        const val TRACKER_PARTS = 8
    }
}
