package com.karan.anuj.core.domain.history

import com.karan.anuj.core.domain.place.FakePlaceRepository
import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.reminder.FakeReminderRepository
import com.karan.anuj.core.domain.reminder.ReminderEvent
import com.karan.anuj.core.domain.reminder.ReminderEventKind
import com.karan.anuj.core.domain.settings.AppSettings
import com.karan.anuj.core.domain.task.Attachment
import com.karan.anuj.core.domain.task.PurgeTasksUseCase
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TaskWorld
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseKeepingTest {

    private val world = TaskWorld()
    private val reminders = FakeReminderRepository()
    private val places = FakePlaceRepository()
    private val purge = PurgeTasksUseCase(world.tasks, world.attachments, world.files, world.settings)
    private val houseKeeping =
        HouseKeepingUseCase(world.settings, world.tasks, purge, world.history, reminders, places, world.files, world.clock)

    private fun days(count: Int) = count * 24L * 60 * 60 * 1_000

    private suspend fun givenLogs(at: Long) {
        world.history.record(listOf(RecordChange("task", "t", "name", "a", "b", at)))
        reminders.log(ReminderEvent("e$at", title = "Water", kind = ReminderEventKind.SHOWN, at = at))
    }

    @Test
    fun `with the defaults nothing is ever removed`() = runTest {
        world.given("old", deletedAt = 1)
        givenLogs(at = 1)
        world.clock.now = days(5_000)

        houseKeeping()

        assertEquals(listOf("old"), world.tasks.all.map { it.name })
        assertEquals(1, world.history.recorded.size)
        assertEquals(1, reminders.events.size)
    }

    @Test
    fun `the trash loses only what has been in it longer than the chosen time`() = runTest {
        world.settings.state.value = AppSettings(emptyTrashAfterDays = 30)
        world.given("long ago", deletedAt = days(1))
        world.given("last week", deletedAt = days(53))
        world.given("live")
        world.clock.now = days(60)

        houseKeeping()

        assertEquals(setOf("last week", "live"), world.tasks.all.map { it.name }.toSet())
    }

    @Test
    fun `logs older than the chosen time are forgotten`() = runTest {
        world.settings.state.value = AppSettings(keepHistoryDays = 90)
        givenLogs(at = days(1))
        givenLogs(at = days(95))
        world.clock.now = days(100)

        houseKeeping()

        assertEquals(listOf(days(95)), world.history.recorded.map { it.changedAt })
        assertEquals(listOf(days(95)), reminders.events.map { it.at })
    }

    @Test
    fun `photos of a task removed for good are held for the chosen time, then deleted`() = runTest {
        world.settings.state.value = AppSettings(keepRemovedPhotosDays = 7)
        world.given("trip", deletedAt = 1)
        world.attachments.save(Attachment("p", TaskId("trip"), "ticket.jpg", RecordStamps.created(0)))
        world.files.now = days(10)

        purge(listOf(TaskId("trip")))
        assertTrue("held, not deleted", world.files.deleted.isEmpty())

        world.clock.now = days(16)
        houseKeeping()
        assertTrue("still inside the week", world.files.deleted.isEmpty())

        world.clock.now = days(18)
        houseKeeping()
        assertEquals(listOf("ticket.jpg"), world.files.deleted)
    }

    @Test
    fun `a change reads as a sentence, with moments as dates`() {
        val zone = ZoneOffset.UTC
        assertEquals(
            "Priority: NONE → HIGH",
            HistoryText.describe(RecordChange("task", "t", "priority", "NONE", "HIGH", 0), zone),
        )
        assertEquals(
            "Completed at: nothing → 1 jan, 12:00 am",
            HistoryText.describe(RecordChange("task", "t", "completedAt", null, "0", 0), zone),
        )
        assertEquals("Due date", HistoryText.field("dueDate"))
    }
}
