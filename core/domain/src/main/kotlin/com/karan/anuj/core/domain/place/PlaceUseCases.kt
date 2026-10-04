package com.karan.anuj.core.domain.place

import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.reminder.ReminderCategory
import com.karan.anuj.core.domain.reminder.ReminderSchedule
import com.karan.anuj.core.domain.reminder.StandingReminderDraft
import com.karan.anuj.core.domain.reminder.StandingRemindersUseCase
import com.karan.anuj.core.domain.task.IdGenerator
import com.karan.anuj.core.domain.task.TaskEditor
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TaskRepository
import com.karan.anuj.core.domain.task.TaskTree
import com.karan.anuj.core.domain.time.TimeSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Turns one reading of where the phone is into what follows from it:
 * arriving, leaving, settling in at home, and noticing a spot the phone
 * keeps stopping at. It is run with every reading and keeps what it needs
 * between readings in [TrackerState], so it works the same whether readings
 * come a minute apart or an hour apart.
 *
 * Two rules protect against false alarms:
 * - Arriving counts only after staying a few minutes, so passing a place does not count.
 * - Leaving counts only when a reading is clearly outside the circle. A
 *   missing or vague reading (indoors, no signal) never means "left".
 */
class TrackPlacesUseCase @Inject constructor(
    private val places: PlaceRepository,
    private val settingsRepository: PlaceSettingsRepository,
    private val tasks: TaskRepository,
    private val editor: TaskEditor,
    private val reminders: StandingRemindersUseCase,
    private val ids: IdGenerator,
) {
    suspend fun onFix(fix: LocationFix) {
        val settings = settingsRepository.settings.first()
        if (!settings.enabled) return
        val all = places.getPlaces()
        var state = settingsRepository.trackerState()

        val inside = state.placeId?.let { id -> all.firstOrNull { it.id == id } }
        if (inside != null) {
            if (!clearlyOutside(fix, inside)) {
                settingsRepository.setTrackerState(whileInside(inside, state, fix, settings))
                return
            }
            leave(inside, state, fix)
        }
        if (state.placeId != null) state = TrackerState()

        val entered = all.filter { contains(it, fix) }.minByOrNull { Geo.metersBetween(fix, it) }
        state = if (entered != null) {
            whileInside(entered, TrackerState(placeId = entered.id, since = fix.at), fix, settings)
        } else {
            onUnknownGround(state, fix, settings)
        }
        settingsRepository.setTrackerState(state)
    }

    /** A reading counts as inside when its centre is in the circle and it is not vaguer than the circle is wide. */
    private fun contains(place: Place, fix: LocationFix): Boolean =
        Geo.metersBetween(fix, place) <= place.radiusMeters && fix.accuracyMeters <= place.radiusMeters * 2

    /** Outside only when even the nearest point the phone could be at is beyond the circle. */
    private fun clearlyOutside(fix: LocationFix, place: Place): Boolean =
        Geo.metersBetween(fix, place) - fix.accuracyMeters > place.radiusMeters

    private suspend fun whileInside(place: Place, before: TrackerState, fix: LocationFix, settings: PlaceSettings): TrackerState {
        var state = before
        val stayed = fix.at - state.since
        var quiet = place.kind == PlaceKind.SEEN

        if (!state.arrived && stayed >= settings.arriveAfterMinutes * MILLIS_PER_MINUTE) {
            state = state.copy(arrived = true)
            places.saveVisit(PlaceVisit(ids.newId(), place.id, arrivedAt = state.since))
            if (place.kind == PlaceKind.SAVED) bringUp(place, PlaceMoment.ARRIVE)
            if (quiet && places.countVisits(place.id) >= settings.noticeAfterVisits) {
                notice(place, settings)
                quiet = false
            }
        }
        if (state.arrived && quiet && stayed >= settings.noticeAfterMinutes * MILLIS_PER_MINUTE) notice(place, settings)
        if (state.arrived && !state.settled && place.isHome && stayed >= settings.settleMinutes * MILLIS_PER_MINUTE) {
            state = state.copy(settled = true)
            startListsFresh(place)
        }
        return state
    }

    private suspend fun leave(place: Place, state: TrackerState, fix: LocationFix) {
        if (!state.arrived) return
        places.openVisit(place.id)?.let { places.saveVisit(it.copy(leftAt = fix.at)) }
        if (place.kind == PlaceKind.SAVED) bringUp(place, PlaceMoment.LEAVE)
    }

    /**
     * A stop on ground no place covers. Once it has lasted [PlaceSettings.stayMinutes]
     * the spot is remembered as a quiet place of its own, and from then on it
     * is handled like any other place.
     */
    private suspend fun onUnknownGround(state: TrackerState, fix: LocationFix, settings: PlaceSettings): TrackerState {
        val anchor = state.anchor
        if (anchor == null || Geo.metersBetween(fix, anchor) > settings.radiusMeters) return TrackerState(anchor = fix)
        if (fix.at - anchor.at < settings.stayMinutes * MILLIS_PER_MINUTE) return state

        val spot = Place(
            id = PlaceId(ids.newId()),
            latitude = anchor.latitude,
            longitude = anchor.longitude,
            radiusMeters = settings.radiusMeters,
            kind = PlaceKind.SEEN,
            stamps = RecordStamps.created(fix.at),
        )
        places.save(spot)
        return whileInside(spot, TrackerState(placeId = spot.id, since = anchor.at), fix, settings)
    }

    /** Asks about a spot once. The answer is given on the Places screen: save it, keep it as visited, or ignore it. */
    private suspend fun notice(place: Place, settings: PlaceSettings) {
        places.save(place.copy(kind = PlaceKind.NOTICED, stamps = place.stamps.touched(editor.now())))
        if (settings.askAboutNewPlaces) notify(NOTICED_TITLE)
    }

    /**
     * Brings up the tasks tied to [place] for this [moment]. An arriving
     * task is named. A leaving list is only mentioned when something on it
     * is still open, and says what.
     */
    private suspend fun bringUp(place: Place, moment: PlaceMoment) {
        val name = place.name.ifBlank { UNNAMED }
        places.tiesOf(place.id).filter { it.moment == moment }.forEach { tie ->
            val task = tasks.get(tie.taskId)?.takeIf { it.isOpen && !it.stamps.isDeleted } ?: return@forEach
            when (moment) {
                PlaceMoment.ARRIVE -> notify("At $name: ${task.name}")
                PlaceMoment.LEAVE -> {
                    val open = openStepsOf(task.id)
                    if (open.isNotEmpty()) notify("Left $name. Not ticked: ${open.joinToString(", ")}")
                }
            }
        }
    }

    /** Back home and settled: every step of each leaving list is reopened, ready for the next time out. */
    private suspend fun startListsFresh(home: Place) {
        places.tiesOf(home.id).filter { it.moment == PlaceMoment.LEAVE }.forEach { tie ->
            val closed = tasks.getSubtree(tie.taskId)
                .filter { it.id != tie.taskId && !it.stamps.isDeleted && !it.isOpen }
            editor.apply(closed.map { it to it.copy(completedAt = null, missedAt = null) })
        }
    }

    private suspend fun openStepsOf(taskId: TaskId): List<String> =
        tasks.getSubtree(taskId)
            .filter { it.parentId == taskId && it.isOpen && !it.stamps.isDeleted }
            .sortedWith(TaskTree.treeOrder)
            .map { it.name }

    /** A place notice is a one-off reminder of the Place kind, so quiet hours, calm mode and the daily limit apply to it. */
    private suspend fun notify(title: String) {
        reminders.add(StandingReminderDraft(title = title, schedule = ReminderSchedule.Once(editor.now())), ReminderCategory.PLACE)
    }

    private companion object {
        const val NOTICED_TITLE = "You keep coming here. Open Places to name this spot, or ignore it"
        const val UNNAMED = "a saved place"
    }
}

/** What the Places screen and a task's place row do. */
class PlaceActions @Inject constructor(
    private val places: PlaceRepository,
    private val settingsRepository: PlaceSettingsRepository,
    private val source: LocationSource,
    private val ids: IdGenerator,
    private val time: TimeSource,
) {
    fun observe(): Flow<List<Place>> = places.observePlaces()

    fun observeSettings(): Flow<PlaceSettings> = settingsRepository.settings

    suspend fun updateSettings(change: (PlaceSettings) -> PlaceSettings) = settingsRepository.update(change)

    val locationPermitted: Boolean get() = source.permitted

    /**
     * Saves the spot the phone is at right now as a place.
     *
     * @return null when no reading could be had; the screen then says to step outside or switch location on
     */
    suspend fun saveHere(name: String, isHome: Boolean): Place? {
        val fix = source.current() ?: return null
        val now = time.nowMillis()
        if (isHome) places.getPlaces().filter { it.isHome }.forEach { places.save(it.copy(isHome = false, stamps = it.stamps.touched(now))) }
        val place = Place(
            id = PlaceId(ids.newId()),
            name = name.trim(),
            latitude = fix.latitude,
            longitude = fix.longitude,
            radiusMeters = settingsRepository.settings.first().radiusMeters,
            kind = PlaceKind.SAVED,
            isHome = isHome,
            stamps = RecordStamps.created(now),
        )
        places.save(place)
        return place
    }

    /** Renames a place, changes what it is, or makes it home. Only one place is home at a time. */
    suspend fun edit(id: PlaceId, change: (Place) -> Place) {
        val current = places.get(id) ?: return
        val now = time.nowMillis()
        val edited = change(current).copy(id = current.id)
        if (edited.isHome && !current.isHome) {
            places.getPlaces().filter { it.isHome }.forEach { places.save(it.copy(isHome = false, stamps = it.stamps.touched(now))) }
        }
        /** Only a saved place can be home or have tasks; anything else keeps neither. */
        val tidy = if (edited.kind == PlaceKind.SAVED) edited else edited.copy(isHome = false)
        places.save(tidy.copy(name = tidy.name.trim(), stamps = tidy.stamps.touched(now)))
    }

    suspend fun remove(id: PlaceId) = places.remove(id)

    fun observeTie(taskId: TaskId): Flow<TaskPlace?> = places.observeTaskPlace(taskId)

    suspend fun tie(taskId: TaskId, tie: TaskPlace?) = places.setTaskPlace(taskId, tie)
}
