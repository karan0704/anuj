package com.karan.anuj.core.domain.place

import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.task.TaskId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow

@JvmInline
value class PlaceId(val value: String)

/** What the user has decided a spot is. Only [SAVED] ever sets off a task or a reminder. */
enum class PlaceKind {
    /** The phone has stopped here at least once, quietly. Nothing has been asked yet. */
    SEEN,

    /** Stayed at long enough, or returned to often enough, that the user has been asked about it. */
    NOTICED,

    /** A place with a name that tasks can be tied to. */
    SAVED,

    /** A record of having been there and nothing more. */
    VISITED,

    /** The user does not want to hear about this spot again. It is kept only so the question is never repeated. */
    IGNORED,
}

/**
 * A circle on the ground.
 *
 * @property name empty until the user gives one
 * @property isHome the one place the leaving list belongs to
 */
data class Place(
    val id: PlaceId,
    val name: String = "",
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val kind: PlaceKind,
    val isHome: Boolean = false,
    val stamps: RecordStamps,
)

/** One stay at a place. [leftAt] is null while the phone is still there. */
data class PlaceVisit(
    val id: String,
    val placeId: PlaceId,
    val arrivedAt: Long,
    val leftAt: Long? = null,
)

enum class PlaceMoment { ARRIVE, LEAVE }

/**
 * A task tied to a place.
 *
 * On [PlaceMoment.ARRIVE] the task is brought up when the phone gets there
 * ("buy the water can" at the shop). On [PlaceMoment.LEAVE] the task is a
 * list to go through before going out: leaving with steps still open brings
 * it up, and its steps start fresh only once the phone has settled back in.
 */
data class TaskPlace(
    val taskId: TaskId,
    val placeId: PlaceId,
    val moment: PlaceMoment,
)

/**
 * One reading of where the phone is.
 *
 * @property accuracyMeters the phone is somewhere within this distance of the reading
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val at: Long,
)

/** Where the phone is, from whatever the phone has: Google's location service first, its own GPS when that gives nothing. */
interface LocationSource {
    /** False until the user has allowed location. */
    val permitted: Boolean

    /** One fresh reading, or null when none could be had (indoors with no signal, location switched off). */
    suspend fun current(): LocationFix?
}

/**
 * Every length and count the place rules use, the user's to change.
 *
 * @property enabled nothing is read or recorded until this is switched on
 * @property radiusMeters how wide a newly saved or newly seen place is
 * @property stayMinutes a stop this long at an unknown spot is remembered, quietly
 * @property noticeAfterMinutes one stay this long makes the app ask about the spot
 * @property noticeAfterVisits or this many separate stays
 * @property arriveAfterMinutes how long inside a place before "arrived" counts, so driving past does not
 * @property settleMinutes how long at home before the leaving list starts fresh
 * @property askAboutNewPlaces off keeps noticing silent: spots are recorded but never asked about
 */
data class PlaceSettings(
    val enabled: Boolean = false,
    val radiusMeters: Int = DEFAULT_RADIUS,
    val stayMinutes: Int = 10,
    val noticeAfterMinutes: Int = 60,
    val noticeAfterVisits: Int = 3,
    val arriveAfterMinutes: Int = 3,
    val settleMinutes: Int = 10,
    val askAboutNewPlaces: Boolean = true,
) {
    companion object {
        const val DEFAULT_RADIUS = 100
        val RADIUS_CHOICES: List<Int> = listOf(50, 100, 150, 250, 500)
        val NOTICE_MINUTES_CHOICES: List<Int> = listOf(30, 60, 120, 180)
        val NOTICE_VISITS_CHOICES: List<Int> = listOf(2, 3, 5, 8)
        val SETTLE_MINUTES_CHOICES: List<Int> = listOf(5, 10, 20, 30)
    }
}

/**
 * What the tracker remembers between two readings.
 *
 * @property placeId the place the phone is inside, if any
 * @property since when it was first seen inside that place, or at [anchor]
 * @property arrived the stay has lasted long enough to count
 * @property settled the leaving list has already been started fresh for this stay
 * @property anchor where a stop on unknown ground began
 */
data class TrackerState(
    val placeId: PlaceId? = null,
    val since: Long = 0,
    val arrived: Boolean = false,
    val settled: Boolean = false,
    val anchor: LocationFix? = null,
)

interface PlaceRepository {
    /** Every place the user can see: [PlaceKind.SEEN] spots are left out. */
    fun observePlaces(): Flow<List<Place>>

    /** Every place, including the quiet ones. */
    suspend fun getPlaces(): List<Place>
    suspend fun get(id: PlaceId): Place?
    suspend fun save(place: Place)

    /** Removes a place with its visits and its ties to tasks. */
    suspend fun remove(id: PlaceId)

    suspend fun saveVisit(visit: PlaceVisit)
    suspend fun openVisit(placeId: PlaceId): PlaceVisit?
    suspend fun countVisits(placeId: PlaceId): Int
    suspend fun deleteVisitsBefore(millis: Long)

    fun observeTaskPlace(taskId: TaskId): Flow<TaskPlace?>
    suspend fun setTaskPlace(taskId: TaskId, tie: TaskPlace?)
    suspend fun tiesOf(placeId: PlaceId): List<TaskPlace>
}

interface PlaceSettingsRepository {
    val settings: Flow<PlaceSettings>
    suspend fun update(change: (PlaceSettings) -> PlaceSettings)
    suspend fun trackerState(): TrackerState
    suspend fun setTrackerState(state: TrackerState)
}

/** Distances on the ground. Accurate to well under a metre at the sizes a place has. */
object Geo {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    fun metersBetween(fix: LocationFix, place: Place): Double =
        metersBetween(fix.latitude, fix.longitude, place.latitude, place.longitude)

    fun metersBetween(a: LocationFix, b: LocationFix): Double = metersBetween(a.latitude, a.longitude, b.latitude, b.longitude)
}
