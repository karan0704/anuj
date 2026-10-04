package com.karan.anuj.core.domain.place

import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.reminder.FakeReminderRepository
import com.karan.anuj.core.domain.reminder.StandingRemindersUseCase
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TaskWorld
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePlaceRepository : PlaceRepository {
    private val rows = MutableStateFlow<Map<PlaceId, Place>>(emptyMap())
    val visits = mutableListOf<PlaceVisit>()
    private val ties = MutableStateFlow<Map<TaskId, TaskPlace>>(emptyMap())

    val all: Collection<Place> get() = rows.value.values

    override fun observePlaces(): Flow<List<Place>> = rows.map { it.values.filter { place -> place.kind != PlaceKind.SEEN } }
    override suspend fun getPlaces(): List<Place> = all.toList()
    override suspend fun get(id: PlaceId): Place? = rows.value[id]
    override suspend fun save(place: Place) = rows.update { it + (place.id to place) }

    override suspend fun remove(id: PlaceId) {
        rows.update { it - id }
        visits.removeAll { it.placeId == id }
        ties.update { all -> all.filterValues { it.placeId != id } }
    }

    override suspend fun saveVisit(visit: PlaceVisit) {
        visits.removeAll { it.id == visit.id }
        visits += visit
    }

    override suspend fun openVisit(placeId: PlaceId): PlaceVisit? = visits.lastOrNull { it.placeId == placeId && it.leftAt == null }
    override suspend fun countVisits(placeId: PlaceId): Int = visits.count { it.placeId == placeId }

    override suspend fun deleteVisitsBefore(millis: Long) {
        visits.removeAll { (it.leftAt ?: Long.MAX_VALUE) < millis }
    }

    override fun observeTaskPlace(taskId: TaskId): Flow<TaskPlace?> = ties.map { it[taskId] }
    override suspend fun setTaskPlace(taskId: TaskId, tie: TaskPlace?) = ties.update { if (tie == null) it - taskId else it + (taskId to tie) }
    override suspend fun tiesOf(placeId: PlaceId): List<TaskPlace> = ties.value.values.filter { it.placeId == placeId }
}

class FakePlaceSettings(initial: PlaceSettings = PlaceSettings(enabled = true)) : PlaceSettingsRepository {
    val state = MutableStateFlow(initial)
    var tracker = TrackerState()
    override val settings: Flow<PlaceSettings> = state
    override suspend fun update(change: (PlaceSettings) -> PlaceSettings) = state.update(change)
    override suspend fun trackerState(): TrackerState = tracker
    override suspend fun setTrackerState(state: TrackerState) {
        tracker = state
    }
}

class PlaceTrackingTest {

    private val world = TaskWorld()
    private val places = FakePlaceRepository()
    private val settings = FakePlaceSettings()
    private val reminders = FakeReminderRepository()
    private val track = TrackPlacesUseCase(
        places, settings, world.tasks, world.editor, StandingRemindersUseCase(reminders, world.ids, world.clock), world.ids,
    )

    /** Home is at this point; one thousandth of a degree north is about 111 metres. */
    private val homeLat = 21.1458
    private val homeLon = 79.0882
    private val notices: List<String> get() = reminders.all.map { it.title }

    private fun minute(n: Int) = n * 60_000L

    private suspend fun at(minute: Int, metresNorth: Int = 0, accuracy: Float = 10f) {
        world.clock.now = minute(minute)
        track.onFix(LocationFix(homeLat + metresNorth / 111_000.0, homeLon, accuracy, at = minute(minute)))
    }

    private suspend fun givenPlace(id: String, kind: PlaceKind = PlaceKind.SAVED, home: Boolean = false, metresNorth: Int = 0): Place {
        val place = Place(
            PlaceId(id), id, homeLat + metresNorth / 111_000.0, homeLon, radiusMeters = 100, kind = kind, isHome = home,
            stamps = RecordStamps.created(0),
        )
        places.save(place)
        return place
    }

    private suspend fun givenLeavingList() {
        givenPlace("Home", home = true)
        world.given("Leaving home")
        world.given("Door locked", parent = "Leaving home")
        world.given("Taps closed", parent = "Leaving home", completedAt = 5)
        places.setTaskPlace(TaskId("Leaving home"), TaskPlace(TaskId("Leaving home"), PlaceId("Home"), PlaceMoment.LEAVE))
    }

    @Test
    fun `nothing is recorded while places are switched off`() = runTest {
        settings.state.value = PlaceSettings(enabled = false)

        at(0)
        at(30)

        assertTrue(places.all.isEmpty())
        assertEquals(TrackerState(), settings.tracker)
    }

    @Test
    fun `arriving counts after a few minutes inside, and brings up the task tied to the place`() = runTest {
        givenPlace("Shop", metresNorth = 2_000)
        world.given("Buy the water can")
        places.setTaskPlace(TaskId("Buy the water can"), TaskPlace(TaskId("Buy the water can"), PlaceId("Shop"), PlaceMoment.ARRIVE))

        at(0, metresNorth = 2_000)
        assertTrue("just got there", notices.isEmpty())

        at(4, metresNorth = 2_010)
        assertEquals(listOf("At Shop: Buy the water can"), notices)

        at(9, metresNorth = 2_010)
        assertEquals("said once per stay", 1, notices.size)
    }

    @Test
    fun `passing a place without stopping is not arriving`() = runTest {
        givenPlace("Shop", metresNorth = 2_000)
        world.given("Buy the water can")
        places.setTaskPlace(TaskId("Buy the water can"), TaskPlace(TaskId("Buy the water can"), PlaceId("Shop"), PlaceMoment.ARRIVE))

        at(0, metresNorth = 2_000)
        at(1, metresNorth = 3_000)

        assertTrue(notices.isEmpty())
        assertTrue(places.visits.isEmpty())
    }

    @Test
    fun `leaving home with a step still open says which`() = runTest {
        givenLeavingList()
        at(0)
        at(5)

        at(20, metresNorth = 600)

        assertEquals(listOf("Left Home. Not ticked: Door locked"), notices)
        assertEquals(minute(20), places.visits.single().leftAt)
    }

    @Test
    fun `leaving home with everything ticked says nothing`() = runTest {
        givenLeavingList()
        world.tasks.save(listOf(world.tasks.task("Door locked").copy(completedAt = 5)))
        at(0)
        at(5)

        at(20, metresNorth = 600)

        assertTrue(notices.isEmpty())
    }

    @Test
    fun `a vague reading indoors is never taken as having left`() = runTest {
        givenLeavingList()
        at(0)
        at(5)

        at(20, metresNorth = 300, accuracy = 400f)

        assertTrue(notices.isEmpty())
        assertNull(places.visits.single().leftAt)
    }

    @Test
    fun `ticks survive passing home and are cleared only after settling back in`() = runTest {
        givenLeavingList()
        at(0)
        at(5)
        world.tasks.save(listOf(world.tasks.task("Door locked").copy(completedAt = 6)))
        at(20, metresNorth = 600)

        at(60)
        at(61, metresNorth = 600)
        assertFalse("only passed by", world.tasks.task("Door locked").isOpen)

        at(120)
        at(125)
        assertFalse("arrived but not settled yet", world.tasks.task("Door locked").isOpen)

        at(131)
        assertTrue(world.tasks.task("Door locked").isOpen)
        assertTrue(world.tasks.task("Taps closed").isOpen)
    }

    @Test
    fun `an hour at an unknown spot is asked about once`() = runTest {
        at(0, metresNorth = 5_000)
        at(12, metresNorth = 5_020)
        assertEquals(PlaceKind.SEEN, places.all.single().kind)
        assertTrue(notices.isEmpty())

        at(61, metresNorth = 5_010)
        at(90, metresNorth = 5_010)

        assertEquals(PlaceKind.NOTICED, places.all.single().kind)
        assertEquals(1, notices.size)
    }

    @Test
    fun `a spot returned to three times is asked about`() = runTest {
        var minute = 0
        repeat(3) {
            at(minute, metresNorth = 5_000)
            at(minute + 12, metresNorth = 5_000)
            at(minute + 20, metresNorth = 9_000)
            minute += 600
        }

        val spot = places.all.single { it.kind != PlaceKind.SEEN || places.countVisits(it.id) > 0 }
        assertEquals(PlaceKind.NOTICED, spot.kind)
        assertEquals(3, places.countVisits(spot.id))
        assertEquals(1, notices.size)
    }

    @Test
    fun `an ignored spot is never asked about again, and a visited one sets nothing off`() = runTest {
        givenPlace("ignored", kind = PlaceKind.IGNORED, metresNorth = 5_000)
        givenPlace("visited", kind = PlaceKind.VISITED, metresNorth = 9_000)
        world.given("task")
        places.setTaskPlace(TaskId("task"), TaskPlace(TaskId("task"), PlaceId("visited"), PlaceMoment.ARRIVE))

        at(0, metresNorth = 5_000)
        at(200, metresNorth = 5_000)
        at(300, metresNorth = 9_000)
        at(310, metresNorth = 9_000)

        assertTrue(notices.isEmpty())
        assertEquals(setOf(PlaceKind.IGNORED, PlaceKind.VISITED), places.all.map { it.kind }.toSet())
        assertNotNull("the visit itself is still recorded", places.openVisit(PlaceId("visited")))
    }

    @Test
    fun `with asking switched off a spot is recorded but nothing is shown`() = runTest {
        settings.state.value = PlaceSettings(enabled = true, askAboutNewPlaces = false)

        at(0, metresNorth = 5_000)
        at(12, metresNorth = 5_000)
        at(70, metresNorth = 5_000)

        assertEquals(PlaceKind.NOTICED, places.all.single().kind)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `distance on the ground is right to within a metre`() {
        assertEquals(111.19, Geo.metersBetween(21.0, 79.0, 21.001, 79.0), 1.0)
        assertEquals(0.0, Geo.metersBetween(21.0, 79.0, 21.0, 79.0), 0.001)
    }
}
