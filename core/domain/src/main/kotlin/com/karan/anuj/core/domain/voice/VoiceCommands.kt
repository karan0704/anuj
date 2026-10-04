package com.karan.anuj.core.domain.voice

import com.karan.anuj.core.domain.reminder.ReminderCategory
import com.karan.anuj.core.domain.reminder.ReminderSchedule
import com.karan.anuj.core.domain.reminder.StandingReminderDraft
import com.karan.anuj.core.domain.reminder.StandingRemindersUseCase
import com.karan.anuj.core.domain.task.CompleteTaskUseCase
import com.karan.anuj.core.domain.task.CreateTaskUseCase
import com.karan.anuj.core.domain.task.Priority
import com.karan.anuj.core.domain.task.SearchTasksUseCase
import com.karan.anuj.core.domain.task.TagRepository
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskRepository
import com.karan.anuj.core.domain.time.TimeSource
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_MINUTE = 60_000L

/** Whether [phrase] appears in order, word for word. */
internal fun List<String>.has(vararg phrase: String): Boolean =
    indexOfPhrase(*phrase) >= 0

internal fun List<String>.indexOfPhrase(vararg phrase: String): Int =
    (0..size - phrase.size).firstOrNull { start -> phrase.indices.all { this[start + it] == phrase[it] } } ?: -1

/** The words after the first of [phrases] found, or null when none is there. */
internal fun List<String>.after(vararg phrases: List<String>): List<String>? {
    for (phrase in phrases) {
        val at = indexOfPhrase(*phrase.toTypedArray())
        if (at >= 0) return drop(at + phrase.size)
    }
    return null
}

/** How a time is said aloud: "6 pm", "6:30 pm". */
fun spokenTime(time: LocalTime): String {
    val hour = ((time.hour + 11) % 12) + 1
    val half = if (time.hour < 12) "am" else "pm"
    return if (time.minute == 0) "$hour $half" else "$hour:${time.minute.toString().padStart(2, '0')} $half"
}

/** A task as it is read out: its name, and its time when it has one today. */
internal fun Task.spoken(today: LocalDate): String = when {
    dueTime != null && dueDate == today -> "$name at ${spokenTime(dueTime)}"
    dueDate?.let { it < today } == true -> "$name, overdue"
    else -> name
}

/** Reads out a list, stopping at the user's limit and saying how many were left out. */
internal fun readOut(heading: String, tasks: List<Task>, context: VoiceContext): VoiceReply {
    val limit = context.settings.maxSpokenItems
    val lines = tasks.take(limit).map { it.spoken(context.today) }
    val rest = tasks.size - lines.size
    return VoiceReply(
        spoken = heading,
        lines = if (rest > 0) lines + "and $rest more" else lines,
        openTask = tasks.singleOrNull()?.id,
    )
}

/** The order tasks are read in: what has a time today first, then by priority, then the oldest. */
internal val readingOrder: Comparator<Task> =
    compareBy<Task> { it.dueTime == null }
        .thenBy { it.dueTime }
        .thenByDescending { it.priority.ordinal }
        .thenBy { it.stamps.createdAt }

/**
 * Finds the task a spoken phrase means. A task matches by how many of its
 * name's words were said; two tasks that match equally well are both
 * returned, so the assistant can ask which one instead of guessing.
 */
internal object TaskMatcher {
    private val IGNORED = setOf("the", "a", "an", "my", "task", "as", "is", "to", "for", "of")
    private const val ENOUGH = 0.5

    fun find(open: List<Task>, said: List<String>): List<Task> {
        val words = said.filterNot { it in IGNORED }.toSet()
        if (words.isEmpty()) return emptyList()
        val scored = open.map { task ->
            val name = SpokenText.words(task.name).filterNot { it in IGNORED }
            task to if (name.isEmpty()) 0.0 else name.count { it in words }.toDouble() / name.size
        }.filter { it.second >= ENOUGH }
        val best = scored.maxOfOrNull { it.second } ?: return emptyList()
        return scored.filter { it.second == best }.map { it.first }
    }
}

class UndoCommand @Inject constructor() : VoiceCommand {
    override val order = 10

    /** Answered by the assistant itself, which remembers what the last command did. */
    override suspend fun answer(words: List<String>, context: VoiceContext): VoiceReply? =
        if (isUndo(words)) VoiceReply("Nothing to undo", understood = true) else null

    companion object {
        fun isUndo(words: List<String>): Boolean =
            words == listOf("undo") || words.has("undo", "that") || words.has("cancel", "that") || words.has("take", "that", "back")
    }
}

class TorchCommand @Inject constructor(private val torch: Torch) : VoiceCommand {
    override val order = 20

    override suspend fun answer(words: List<String>, context: VoiceContext): VoiceReply? {
        if ("flashlight" !in words && "torch" !in words && !words.has("flash", "light")) return null
        val on = "off" !in words
        return if (torch.set(on)) {
            VoiceReply(if (on) "Flashlight on" else "Flashlight off", undo = VoiceUndo.Torch(wasOn = !on))
        } else {
            VoiceReply("This phone would not switch the flashlight")
        }
    }
}

/** "Set a timer for ten minutes": a one-off reminder, so it rings through the same engine as every other. */
class TimerCommand @Inject constructor(
    private val reminders: StandingRemindersUseCase,
    private val time: TimeSource,
) : VoiceCommand {
    override val order = 30

    override suspend fun answer(words: List<String>, context: VoiceContext): VoiceReply? {
        if ("timer" !in words) return null
        val minutes = minutesIn(words) ?: return VoiceReply("For how long? Say, set a timer for ten minutes", understood = false)
        val label = if (minutes % MINUTES_PER_HOUR == 0 && minutes >= MINUTES_PER_HOUR) {
            "${minutes / MINUTES_PER_HOUR} hour"
        } else {
            "$minutes minute"
        }
        val reminder = reminders.add(
            StandingReminderDraft(
                title = "$label timer",
                schedule = ReminderSchedule.Once(time.nowMillis() + minutes * MILLIS_PER_MINUTE),
            ),
            category = ReminderCategory.TASK,
        )
        return VoiceReply("Timer set for $minutes minutes", undo = VoiceUndo.Timer(reminder))
    }

    private fun minutesIn(words: List<String>): Int? {
        for (index in words.indices) {
            val (value, length) = SpokenText.numberAt(words, index) ?: continue
            val unit = words.getOrNull(index + length) ?: continue
            if (unit.startsWith("minute")) return value
            if (unit.startsWith("hour")) return value * MINUTES_PER_HOUR
        }
        if (words.has("an", "hour")) return MINUTES_PER_HOUR
        if (words.has("half", "an", "hour")) return MINUTES_PER_HOUR / 2
        return null
    }
}

class AddTaskCommand @Inject constructor(private val create: CreateTaskUseCase) : VoiceCommand {
    override val order = 40

    override suspend fun answer(words: List<String>, context: VoiceContext): VoiceReply? {
        if (LEAD_INS.none { words.take(it.size) == it }) return null
        val draft = SpokenTaskParser.parse(words.joinToString(" "), context.today, context.dayParts)
        val task = create(draft.copy(dueDate = draft.dueDate ?: context.today), context.today)
            ?: return VoiceReply("What should the task be called?", understood = false)
        val day = when (task.dueDate) {
            context.today -> "today"
            context.today.plusDays(1) -> "tomorrow"
            else -> "on ${task.dueDate?.dayOfWeek?.name?.lowercase()?.replaceFirstChar { it.uppercase() }}"
        }
        val at = task.dueTime?.let { " at ${spokenTime(it)}" }.orEmpty()
        return VoiceReply("Added ${task.name}, $day$at", undo = VoiceUndo.Created(task.id), openTask = task.id)
    }

    private companion object {
        val LEAD_INS = listOf(
            listOf("add"), listOf("new", "task"), listOf("create"), listOf("remind", "me"),
            listOf("i", "need", "to"), listOf("i", "have", "to"),
        )
    }
}

class SearchCommand @Inject constructor(private val search: SearchTasksUseCase) : VoiceCommand {
    override val order = 50

    override suspend fun answer(words: List<String>, context: VoiceContext): VoiceReply? {
        val wanted = words.after(listOf("search", "for"), listOf("look", "for"), listOf("search"), listOf("find")) ?: return null
        if (wanted.isEmpty()) return VoiceReply("Search for what?", understood = false)
        val phrase = wanted.joinToString(" ")
        val found = search(phrase).filter { it.isOpen }.sortedWith(readingOrder)
        return if (found.isEmpty()) VoiceReply("Nothing found for $phrase") else readOut("${found.size} found for $phrase", found, context)
    }
}

class MarkDoneCommand @Inject constructor(
    private val tasks: TaskRepository,
    private val complete: CompleteTaskUseCase,
) : VoiceCommand {
    override val order = 60

    override suspend fun answer(words: List<String>, context: VoiceContext): VoiceReply? {
        val said = when {
            words.firstOrNull() in setOf("mark", "tick", "complete", "finish") -> words.drop(1).filterNot { it == "done" || it == "off" }
            else -> words.after(listOf("done", "with"), listOf("i", "finished"), listOf("i", "did"), listOf("finished"), listOf("i", "have", "done"))
        } ?: return null
        val matches = TaskMatcher.find(tasks.getOpen(), said)
        return when (matches.size) {
            0 -> VoiceReply("I could not find that task", understood = false)
            1 -> {
                val task = matches.single()
                val undo = complete(task.id, context.today) ?: return VoiceReply("${task.name} is already done")
                VoiceReply("Done: ${task.name}", undo = VoiceUndo.Completed(undo))
            }
            else -> VoiceReply("Which one?", lines = matches.map { it.name }, understood = false)
        }
    }
}

/** "Read the steps of leaving home": the steps not ticked yet. "Checklist" is heard as the same thing. */
class StepsCommand @Inject constructor(
    private val tasks: TaskRepository,
) : VoiceCommand {
    override val order = 70

    override suspend fun answer(words: List<String>, context: VoiceContext): VoiceReply? {
        val said = words.after(
            listOf("checklist", "of"), listOf("checklist", "for"), listOf("steps", "of"), listOf("steps", "for"),
            listOf("what", "do", "i", "need", "for"), listOf("what", "is", "in"), listOf("what's", "in"),
        ) ?: return null
        val open = tasks.getOpen()
        val task = TaskMatcher.find(open, said).firstOrNull() ?: return VoiceReply("I could not find that task", understood = false)
        val steps = open.filter { it.parentId == task.id }.sortedBy { it.stamps.createdAt }.map { it.name }
        return if (steps.isEmpty()) {
            VoiceReply("${task.name} has nothing left to tick", openTask = task.id)
        } else {
            VoiceReply("${task.name}, ${steps.size} left", lines = steps, openTask = task.id)
        }
    }
}

/**
 * "What should I do now?" One answer, never a list: something already due
 * by the clock, else the most important thing for today, else the next
 * thing that has a time.
 */
class WhatNowCommand @Inject constructor(private val tasks: TaskRepository) : VoiceCommand {
    override val order = 80

    override suspend fun answer(words: List<String>, context: VoiceContext): VoiceReply? {
        val asked = words.has("what", "should", "i", "do") || words.has("what", "do", "i", "do") ||
            words.has("what's", "next") || words.has("what", "is", "next") || words.has("what", "next") ||
            words.has("what", "now")
        if (!asked) return null
        val pick = pick(tasks.getOpen(), context) ?: return VoiceReply("Nothing is due. Enjoy the quiet")
        return VoiceReply("Do this now: ${pick.spoken(context.today)}", openTask = pick.id)
    }

    companion object {
        /** A task counts as due by the clock from this many minutes before its time. */
        private const val SOON_MINUTES = 30L

        fun pick(open: List<Task>, context: VoiceContext): Task? {
            val due = open.filter { task -> task.parentId == null && task.dueDate?.let { it <= context.today } == true }
            val soon = context.now.plusMinutes(SOON_MINUTES)
            /** Near midnight "half an hour from now" is tomorrow; then everything timed today is already due. */
            val pastMidnight = soon < context.now
            val dueByClock = due
                .filter { task -> task.dueDate == context.today && task.dueTime?.let { pastMidnight || it <= soon } == true }
                .minByOrNull { it.dueTime ?: LocalTime.MAX }
            return dueByClock
                ?: due.filter { it.priority != Priority.NONE }.maxWithOrNull(compareBy<Task> { it.priority.ordinal }.thenByDescending { it.stamps.createdAt })
                ?: due.filter { it.dueTime != null }.minByOrNull { it.dueTime ?: LocalTime.MAX }
                ?: due.minByOrNull { it.stamps.createdAt }
        }
    }
}

class PriorityTasksCommand @Inject constructor(private val tasks: TaskRepository) : VoiceCommand {
    override val order = 90

    override suspend fun answer(words: List<String>, context: VoiceContext): VoiceReply? {
        val asked = words.has("high", "priority") || "important" in words || "urgent" in words || words.has("top", "priority")
        if (!asked) return null
        val found = tasks.getOpen().filter { it.priority == Priority.HIGH }.sortedWith(readingOrder)
        return if (found.isEmpty()) {
            VoiceReply("No high priority tasks")
        } else {
            readOut("${found.size} high priority ${if (found.size == 1) "task" else "tasks"}", found, context)
        }
    }
}

/**
 * "Office tasks", "tasks related to money". The subject is matched against
 * tag names and against the words in a task's name, widened by the user's
 * topic words, so "office" finds what is tagged Work.
 */
class TopicTasksCommand @Inject constructor(
    private val tasks: TaskRepository,
    private val tags: TagRepository,
) : VoiceCommand {
    override val order = 100

    override suspend fun answer(words: List<String>, context: VoiceContext): VoiceReply? {
        val topic = topicIn(words) ?: return null
        val wanted = wordsFor(topic, context.settings.topics)
        val tagIds = tags.getAll().filter { tag -> SpokenText.words(tag.name).any { it in wanted } }.map { it.id }.toSet()
        val found = tasks.getOpen().filter { task ->
            task.tagIds.any { it in tagIds } || SpokenText.words(task.name).any { it in wanted }
        }.sortedWith(readingOrder)
        return if (found.isEmpty()) {
            VoiceReply("No $topic tasks")
        } else {
            readOut("${found.size} $topic ${if (found.size == 1) "task" else "tasks"}", found, context)
        }
    }

    private fun topicIn(words: List<String>): String? {
        val after = words.after(
            listOf("tasks", "related", "to"), listOf("task", "related", "to"), listOf("tasks", "about"),
            listOf("tasks", "for"), listOf("tasks", "tagged"), listOf("tasks", "with", "tag"),
        )
        if (after != null) return after.filterNot { it in FILLER }.firstOrNull()
        val at = words.indexOfFirst { it == "tasks" || it == "task" }
        return words.getOrNull(at - 1)?.takeIf { at > 0 && it !in FILLER }
    }

    private fun wordsFor(topic: String, topics: Map<String, Set<String>>): Set<String> {
        val group = topics.entries.firstOrNull { (name, others) -> topic == name || topic in others }
        return (group?.let { it.value + it.key } ?: emptySet()) + topic
    }

    private companion object {
        /** Words that come before "tasks" without naming a subject. */
        val FILLER = setOf(
            "my", "the", "all", "of", "list", "today's", "todays", "today", "open", "any", "me", "are", "what", "pending",
            "remaining", "these", "those", "a", "an", "some", "your", "our", "have", "i",
            "show", "tell", "read", "give", "get", "see", "say", "out", "and", "other", "more",
        )
    }
}

class ListTasksCommand @Inject constructor(private val tasks: TaskRepository) : VoiceCommand {
    override val order = 110

    override suspend fun answer(words: List<String>, context: VoiceContext): VoiceReply? {
        val asked = "tasks" in words || words.has("task", "list") || words.has("to", "do") || words.has("my", "list") ||
            words.has("what", "do", "i", "have")
        if (!asked) return null
        val open = tasks.getOpen().filter { it.parentId == null }
        val today = open.filter { task -> task.dueDate?.let { it <= context.today } == true }.sortedWith(readingOrder)
        if (today.isNotEmpty()) {
            return readOut("${today.size} ${if (today.size == 1) "task" else "tasks"} for today", today, context)
        }
        val undated = open.filter { it.dueDate == null }.sortedWith(readingOrder)
        return if (undated.isEmpty()) {
            VoiceReply("Nothing for today")
        } else {
            readOut("Nothing for today. ${undated.size} in the inbox", undated, context)
        }
    }
}
