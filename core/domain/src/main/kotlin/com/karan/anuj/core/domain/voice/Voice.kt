package com.karan.anuj.core.domain.voice

import com.karan.anuj.core.domain.reminder.Reminder
import com.karan.anuj.core.domain.task.CompletionUndo
import com.karan.anuj.core.domain.task.DayParts
import com.karan.anuj.core.domain.task.TaskId
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

/** When the assistant keeps the microphone open, waiting for its name. */
enum class ListenWhen { NEVER, WHILE_CHARGING, WHILE_SCREEN_ON, ALWAYS }

/**
 * @property wakeName the name that wakes the assistant, as the user writes it
 * @property soundsLike what the speech engine actually writes when the user
 * says that name. An offline engine only knows dictionary words, so a name
 * like "Anuj" comes out as other words; those are learnt once by saying the
 * name a few times, and any of them wakes the assistant.
 * @property maxSpokenItems how many tasks are read out before "and N more"
 * @property topics words that mean the same subject, so "office tasks" finds
 * a task tagged Work and "tasks related to money" finds "Pay the rent"
 */
data class VoiceSettings(
    val wakeName: String = DEFAULT_WAKE_NAME,
    val soundsLike: Set<String> = emptySet(),
    val listenWhen: ListenWhen = ListenWhen.NEVER,
    val speakReplies: Boolean = true,
    val maxSpokenItems: Int = DEFAULT_SPOKEN_ITEMS,
    val listenSeconds: Int = DEFAULT_LISTEN_SECONDS,
    val topics: Map<String, Set<String>> = DEFAULT_TOPICS,
) {
    /** Every phrase that counts as the name, longest first so the whole phrase is matched before a part of it. */
    val wakePhrases: List<String>
        get() = (soundsLike + wakeName).map(SpokenText::normalised).filter { it.isNotEmpty() }
            .distinct().sortedByDescending { it.length }

    companion object {
        const val DEFAULT_WAKE_NAME = "Anuj"
        const val DEFAULT_SPOKEN_ITEMS = 5
        const val DEFAULT_LISTEN_SECONDS = 8
        val SPOKEN_ITEM_CHOICES: List<Int> = listOf(3, 5, 8, 12)
        val LISTEN_SECONDS_CHOICES: List<Int> = listOf(5, 8, 12, 20)
        val DEFAULT_TOPICS: Map<String, Set<String>> = mapOf(
            "work" to setOf("office", "job", "meeting", "boss", "client", "project"),
            "money" to setOf("pay", "bill", "bills", "bank", "rent", "salary", "loan", "fee", "fees", "tax", "payment"),
            "home" to setOf("house", "clean", "laundry", "kitchen", "repair"),
            "health" to setOf("doctor", "dentist", "medicine", "medication", "gym", "exercise", "hospital"),
            "errands" to setOf("buy", "shop", "shopping", "pick", "collect", "post"),
        )
    }
}

interface VoiceSettingsRepository {
    val settings: Flow<VoiceSettings>
    suspend fun update(change: (VoiceSettings) -> VoiceSettings)
}

/** The phone's flashlight. Behind an interface so the command that uses it is tested without a phone. */
interface Torch {
    /** @return false when the phone has no flashlight or refuses */
    suspend fun set(on: Boolean): Boolean
}

/** Why the microphone could not be used. Each one has its own message and its own way out. */
enum class SpeechFailure { NO_PERMISSION, NO_MODEL, MICROPHONE_BUSY }

/** What the speech engine reports while it listens. */
sealed interface Speech {
    /** Words so far, still changing as more is heard. */
    data class Partial(val text: String) : Speech

    /** A finished sentence: the speaker paused. */
    data class Final(val text: String) : Speech

    data class Failed(val reason: SpeechFailure) : Speech
}

/**
 * Turns speech into text on the phone, with no internet. Behind an
 * interface so the engine can be swapped for a better one without touching
 * the assistant or any screen.
 */
interface SpeechEngine {
    /** The microphone is open for as long as this is collected, and closed when collection stops. */
    fun listen(): Flow<Speech>
}

/** Reads an answer aloud. */
interface Speaker {
    /** Returns when the speaking has finished, so the microphone is not opened on the assistant's own voice. */
    suspend fun say(text: String)
    fun stop()
}

/** What a command needs to know about the moment it was spoken. */
data class VoiceContext(
    val today: LocalDate,
    val now: LocalTime,
    val settings: VoiceSettings = VoiceSettings(),
    val dayParts: DayParts = DayParts(),
)

/** How to take back what a command did. */
sealed interface VoiceUndo {
    data class Completed(val undo: CompletionUndo) : VoiceUndo
    data class Created(val taskId: TaskId) : VoiceUndo
    data class Timer(val reminder: Reminder) : VoiceUndo
    data class Torch(val wasOn: Boolean) : VoiceUndo
}

/**
 * The answer to something spoken.
 *
 * @property spoken read aloud, and shown as the heading
 * @property lines shown under the heading and read after it, one by one
 * @property undo set when the command changed something
 * @property openTask the task the answer is about, so a tap can open it
 * @property understood false for "I did not catch that"
 */
data class VoiceReply(
    val spoken: String,
    val lines: List<String> = emptyList(),
    val undo: VoiceUndo? = null,
    val openTask: TaskId? = null,
    val understood: Boolean = true,
) {
    /** The whole answer as one piece of speech. */
    val speech: String get() = (listOf(spoken) + lines).joinToString(". ")
}

/**
 * One thing the assistant can be asked. A new ability is a new
 * implementation; nothing that exists is edited.
 *
 * @property order commands are asked lowest first, so a narrow phrase
 * ("high priority tasks") is tried before a wide one ("tasks")
 */
interface VoiceCommand {
    val order: Int

    /** @return null when [words] are not this command's phrase */
    suspend fun answer(words: List<String>, context: VoiceContext): VoiceReply?
}
