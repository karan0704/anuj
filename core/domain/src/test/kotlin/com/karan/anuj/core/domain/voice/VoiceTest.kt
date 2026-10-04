package com.karan.anuj.core.domain.voice

import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.reminder.FakeReminderRepository
import com.karan.anuj.core.domain.reminder.ReminderSchedule
import com.karan.anuj.core.domain.reminder.StandingRemindersUseCase
import com.karan.anuj.core.domain.task.CompleteTaskUseCase
import com.karan.anuj.core.domain.task.CreateTaskUseCase
import com.karan.anuj.core.domain.task.DeleteTaskUseCase
import com.karan.anuj.core.domain.task.MONDAY
import com.karan.anuj.core.domain.task.Priority
import com.karan.anuj.core.domain.task.SearchTasksUseCase
import com.karan.anuj.core.domain.task.Tag
import com.karan.anuj.core.domain.task.TagId
import com.karan.anuj.core.domain.task.TagRepository
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TaskWorld
import com.karan.anuj.core.domain.task.UndoCompletionUseCase
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTorch(var on: Boolean = false, private val works: Boolean = true) : Torch {
    override suspend fun set(on: Boolean): Boolean {
        if (works) this.on = on
        return works
    }
}

private class FakeVoiceSettings(initial: VoiceSettings = VoiceSettings()) : VoiceSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<VoiceSettings> = state
    val now: VoiceSettings get() = state.value
    override suspend fun update(change: (VoiceSettings) -> VoiceSettings) = state.update(change)
}

private class FakeTags(private val tags: MutableList<Tag> = mutableListOf()) : TagRepository {
    override fun observeAll(): Flow<List<Tag>> = MutableStateFlow(tags.toList())
    override suspend fun getAll(): List<Tag> = tags.toList()
    override suspend fun save(tags: List<Tag>) {
        this.tags += tags
    }
}

/** Everything the assistant needs, built on the in-memory task and reminder stores. */
private class VoiceWorld {
    val world = TaskWorld()
    val reminders = FakeReminderRepository()
    val torch = FakeTorch()
    val settings = FakeVoiceSettings()
    val tags = FakeTags()
    val standing = StandingRemindersUseCase(reminders, world.ids, world.clock)
    val complete = CompleteTaskUseCase(world.tasks, world.editor, world.ids)

    val assistant = VoiceAssistant(
        commands = setOf(
            UndoCommand(),
            TorchCommand(torch),
            TimerCommand(standing, world.clock),
            AddTaskCommand(CreateTaskUseCase(world.tasks, world.ids, world.clock)),
            SearchCommand(SearchTasksUseCase(world.tasks)),
            MarkDoneCommand(world.tasks, complete),
            ChecklistCommand(world.tasks, world.checklists),
            WhatNowCommand(world.tasks),
            PriorityTasksCommand(world.tasks),
            TopicTasksCommand(world.tasks, tags),
            ListTasksCommand(world.tasks),
        ),
        settings = settings,
        taskPreferences = world.preferences,
        undoCompletion = UndoCompletionUseCase(world.tasks, world.checklists, world.editor),
        deleteTask = DeleteTaskUseCase(world.tasks, world.editor),
        standing = standing,
        torch = torch,
    )

    suspend fun task(
        name: String,
        due: LocalDate? = MONDAY,
        time: LocalTime? = null,
        priority: Priority = Priority.NONE,
        tag: String? = null,
        parent: String? = null,
    ): Task {
        world.clock.now += 1
        val task = Task(
            id = TaskId(name),
            parentId = parent?.let(::TaskId),
            name = name,
            dueDate = due,
            dueTime = time,
            priority = priority,
            tagIds = setOfNotNull(tag?.let(::TagId)),
            stamps = RecordStamps.created(world.clock.now),
        )
        world.tasks.save(listOf(task))
        return task
    }

    suspend fun say(text: String, at: LocalTime = LocalTime.of(10, 0)): VoiceReply = assistant.answer(text, MONDAY, at)
}

class SpokenTaskParserTest {

    private fun parse(text: String) = SpokenTaskParser.parse(text, MONDAY)

    @Test
    fun `a day and a time are taken out of the name`() {
        val draft = parse("call mum tomorrow at six p m")
        assertEquals("Call mum", draft.name)
        assertEquals(MONDAY.plusDays(1), draft.dueDate)
        assertEquals(LocalTime.of(18, 0), draft.dueTime)
    }

    @Test
    fun `a time said in words with minutes is understood`() {
        assertEquals(LocalTime.of(18, 30), parse("dentist at six thirty").dueTime)
        assertEquals(LocalTime.of(9, 15), parse("standup at nine fifteen a m").dueTime)
        assertEquals(LocalTime.of(6, 30), parse("run 6:30 am").dueTime)
    }

    @Test
    fun `a time alone means today`() {
        val draft = parse("pay rent at five pm")
        assertEquals(MONDAY, draft.dueDate)
        assertEquals("Pay rent", draft.name)
    }

    @Test
    fun `a number that is not a time stays in the name`() {
        val draft = parse("buy two apples")
        assertEquals("Buy two apples", draft.name)
        assertNull(draft.dueTime)
    }

    @Test
    fun `a weekday means the next one, never today`() {
        assertEquals(MONDAY.plusDays(7), parse("gym on monday").dueDate)
        assertEquals(MONDAY.plusDays(4), parse("call the bank friday").dueDate)
    }

    @Test
    fun `a part of the day uses the user's own time for it`() {
        val draft = parse("water the plants tomorrow morning")
        assertEquals(LocalTime.of(8, 0), draft.dueTime)
        assertEquals("Water the plants", draft.name)
    }

    @Test
    fun `morning in a name is not a time`() {
        val draft = parse("do the morning routine")
        assertEquals("Do the morning routine", draft.name)
        assertNull(draft.dueTime)
    }

    @Test
    fun `tonight is today at the night time`() {
        val draft = parse("take out the bins tonight")
        assertEquals(MONDAY, draft.dueDate)
        assertEquals(LocalTime.of(21, 0), draft.dueTime)
    }

    @Test
    fun `the lead-in and an importance word are not part of the name`() {
        val draft = parse("remind me to renew the passport important")
        assertEquals("Renew the passport", draft.name)
        assertEquals(Priority.HIGH, draft.priority)
    }
}

class WakePhraseTest {

    @Test
    fun `the name wakes the assistant and the rest is the command`() {
        val heard = WakePhrase.find("Anuj what should I do now", VoiceSettings())
        assertTrue(heard.woke)
        assertEquals("what should i do now", heard.command)
    }

    @Test
    fun `the name at the end counts too`() {
        val heard = WakePhrase.find("what's next anuj", VoiceSettings())
        assertTrue(heard.woke)
        assertEquals("what's next", heard.command)
    }

    @Test
    fun `what the engine writes for the name wakes it as well`() {
        val settings = VoiceSettings(soundsLike = setOf("a new j", "and you"))
        assertTrue(WakePhrase.find("hey a new j flashlight on", settings).woke)
        assertEquals("flashlight on", WakePhrase.find("hey a new j flashlight on", settings).command)
    }

    @Test
    fun `speech without the name does not wake it`() {
        assertFalse(WakePhrase.find("what should i do now", VoiceSettings()).woke)
    }

    @Test
    fun `a name inside another word does not wake it`() {
        assertFalse(WakePhrase.find("the anujan road", VoiceSettings()).woke)
    }
}

class VoiceAssistantTest {

    @Test
    fun `the list of tasks reads today's tasks, timed ones first`() = runTest {
        val voice = VoiceWorld()
        voice.task("Call the bank")
        voice.task("Dentist", time = LocalTime.of(18, 0))
        voice.task("Next week thing", due = MONDAY.plusDays(7))

        val reply = voice.say("tell me the list of tasks")

        assertEquals("2 tasks for today", reply.spoken)
        assertEquals(listOf("Dentist at 6 pm", "Call the bank"), reply.lines)
    }

    @Test
    fun `a long list stops at the limit and says how many are left`() = runTest {
        val voice = VoiceWorld()
        repeat(7) { voice.task("Task $it") }

        val reply = voice.say("what are my tasks")

        assertEquals(6, reply.lines.size)
        assertEquals("and 2 more", reply.lines.last())
    }

    @Test
    fun `steps are not read as tasks of their own`() = runTest {
        val voice = VoiceWorld()
        voice.task("Morning routine")
        voice.task("Brush teeth", parent = "Morning routine")

        assertEquals(listOf("Morning routine"), voice.say("list my tasks").lines)
    }

    @Test
    fun `high priority tasks are read on their own`() = runTest {
        val voice = VoiceWorld()
        voice.task("Renew passport", priority = Priority.HIGH)
        voice.task("Water plants")

        val reply = voice.say("tell me the high priority tasks")

        assertEquals("1 high priority task", reply.spoken)
        assertEquals(listOf("Renew passport"), reply.lines)
    }

    @Test
    fun `office tasks finds what is tagged Work`() = runTest {
        val voice = VoiceWorld()
        voice.tags.save(listOf(Tag(TagId("w"), "Work", 0, RecordStamps.created(1))))
        voice.task("Send the report", tag = "w")
        voice.task("Water plants")

        val reply = voice.say("office tasks")

        assertEquals("1 office task", reply.spoken)
        assertEquals(listOf("Send the report"), reply.lines)
    }

    @Test
    fun `tasks related to money finds them by the words in their names`() = runTest {
        val voice = VoiceWorld()
        voice.task("Pay the rent")
        voice.task("Call the bank")
        voice.task("Water plants")

        val reply = voice.say("tasks related to money")

        assertEquals("2 money tasks", reply.spoken)
        assertEquals(setOf("Pay the rent", "Call the bank"), reply.lines.toSet())
    }

    @Test
    fun `what should I do now picks the thing due by the clock`() = runTest {
        val voice = VoiceWorld()
        voice.task("Renew passport", priority = Priority.HIGH)
        voice.task("Dentist", time = LocalTime.of(10, 15))

        val reply = voice.say("what should I do now", at = LocalTime.of(10, 0))

        assertEquals("Do this now: Dentist at 10:15 am", reply.spoken)
        assertEquals(TaskId("Dentist"), reply.openTask)
    }

    @Test
    fun `with nothing due by the clock it picks the most important`() = runTest {
        val voice = VoiceWorld()
        voice.task("Water plants")
        voice.task("Renew passport", priority = Priority.HIGH)
        voice.task("Dentist", time = LocalTime.of(18, 0))

        assertEquals("Do this now: Renew passport", voice.say("what should I do now").spoken)
    }

    @Test
    fun `with nothing due it says so`() = runTest {
        assertEquals("Nothing is due. Enjoy the quiet", VoiceWorld().say("what's next").spoken)
    }

    @Test
    fun `a checklist is read without the lines already ticked`() = runTest {
        val voice = VoiceWorld()
        voice.task("Leaving home")
        voice.world.givenChecklistItem("Keys", "Leaving home", checked = true)
        voice.world.givenChecklistItem("Door locked", "Leaving home", checked = false)

        val reply = voice.say("read the checklist of leaving home")

        assertEquals("Leaving home, 1 left", reply.spoken)
        assertEquals(listOf("Door locked"), reply.lines)
    }

    @Test
    fun `adding a task by voice makes it and undo removes it`() = runTest {
        val voice = VoiceWorld()

        val reply = voice.say("add call mum tomorrow at six pm")
        assertEquals("Added Call mum, tomorrow at 6 pm", reply.spoken)
        val made = voice.world.tasks.all.single()
        assertEquals(LocalTime.of(18, 0), made.dueTime)

        assertEquals("Undone", voice.say("undo").spoken)
        assertTrue(voice.world.tasks.all.single().stamps.isDeleted)
    }

    @Test
    fun `a task added with no day is due today`() = runTest {
        val voice = VoiceWorld()
        voice.say("add buy milk")
        assertEquals(MONDAY, voice.world.tasks.all.single().dueDate)
    }

    @Test
    fun `marking done finds the task by its name and undo reopens it`() = runTest {
        val voice = VoiceWorld()
        voice.task("Call the bank")
        voice.task("Water plants")

        assertEquals("Done: Call the bank", voice.say("mark call the bank as done").spoken)
        assertTrue(voice.world.tasks.task("Call the bank").isDone)

        voice.say("undo")
        assertFalse(voice.world.tasks.task("Call the bank").isDone)
    }

    @Test
    fun `two tasks that match equally are asked about, not guessed`() = runTest {
        val voice = VoiceWorld()
        voice.task("Call mum")
        voice.task("Call dad")

        val reply = voice.say("done with call")

        assertFalse(reply.understood)
        assertEquals(setOf("Call mum", "Call dad"), reply.lines.toSet())
        assertFalse(voice.world.tasks.task("Call mum").isDone)
    }

    @Test
    fun `a timer becomes a one-off reminder and undo removes it`() = runTest {
        val voice = VoiceWorld()
        voice.world.clock.now = 1_000_000

        assertEquals("Timer set for 10 minutes", voice.say("set a timer for ten minutes").spoken)
        val timer = voice.reminders.all.single()
        assertEquals(ReminderSchedule.Once(1_000_000 + 10 * 60_000L), timer.schedule)

        voice.say("undo")
        assertTrue(voice.reminders.all.single().stamps.isDeleted)
    }

    @Test
    fun `the flashlight goes on and off and undo puts it back`() = runTest {
        val voice = VoiceWorld()

        voice.say("flashlight on")
        assertTrue(voice.torch.on)
        voice.say("turn the torch off")
        assertFalse(voice.torch.on)
        voice.say("undo")
        assertTrue(voice.torch.on)
    }

    @Test
    fun `search reads what it finds`() = runTest {
        val voice = VoiceWorld()
        voice.task("Renew passport")
        voice.task("Water plants")

        assertEquals(listOf("Renew passport"), voice.say("search for passport").lines)
    }

    @Test
    fun `something it does not know is answered with what to try`() = runTest {
        val reply = VoiceWorld().say("sing me a song")
        assertFalse(reply.understood)
        assertEquals("I did not catch that", reply.spoken)
    }

    @Test
    fun `undo with nothing done says so`() = runTest {
        assertEquals("Nothing to undo", VoiceWorld().say("undo").spoken)
    }

    @Test
    fun `learning the name keeps what was heard and a new name forgets it`() = runTest {
        val settings = FakeVoiceSettings()
        val useCase = VoiceSettingsUseCase(settings)

        useCase.learnName("A new J")
        useCase.learnName("this is a whole sentence and not a name")
        assertEquals(setOf("a new j"), settings.now.soundsLike)

        useCase.setWakeName("Mitra")
        assertEquals("Mitra", settings.now.wakeName)
        assertTrue(settings.now.soundsLike.isEmpty())
    }
}
