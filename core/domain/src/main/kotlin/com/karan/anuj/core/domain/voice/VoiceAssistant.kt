package com.karan.anuj.core.domain.voice

import com.karan.anuj.core.domain.reminder.StandingRemindersUseCase
import com.karan.anuj.core.domain.reminder.ZoneSource
import com.karan.anuj.core.domain.task.DeleteTaskUseCase
import com.karan.anuj.core.domain.task.TaskPreferencesRepository
import com.karan.anuj.core.domain.task.UndoCompletionUseCase
import com.karan.anuj.core.domain.time.TimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Finds the assistant's name in what was heard.
 *
 * The name can be anywhere in the sentence: "Anuj, what's next" and
 * "what's next, Anuj" both count, and whatever is left is the command.
 */
object WakePhrase {

    /** What was heard: the name was in it or not, and the words that were not the name. */
    data class Heard(val woke: Boolean, val command: String)

    fun find(text: String, settings: VoiceSettings): Heard {
        val heard = " ${SpokenText.normalised(text)} "
        for (phrase in settings.wakePhrases) {
            val at = heard.indexOf(" $phrase ")
            if (at < 0) continue
            val rest = (heard.substring(0, at) + " " + heard.substring(at + phrase.length + 2)).trim()
            return Heard(woke = true, command = rest.removePrefix("hey ").removePrefix("ok ").removePrefix("okay ").trim())
        }
        return nearly(SpokenText.words(text), settings) ?: Heard(woke = false, command = heard.trim())
    }

    /**
     * The same name is rarely written the same way twice by the engine: "a
     * new j" one time, "a new jay" the next. So a run of words that is
     * within a letter or two of a known phrase counts as the name too.
     */
    private fun nearly(words: List<String>, settings: VoiceSettings): Heard? {
        for (phrase in settings.wakePhrases) {
            val length = SpokenText.words(phrase).size
            val allowed = maxOf(1, (phrase.length + LETTERS_PER_SLIP / 2) / LETTERS_PER_SLIP)
            for (start in 0..words.size - length) {
                val run = words.subList(start, start + length).joinToString(" ")
                if (distance(run, phrase) > allowed) continue
                val rest = (words.take(start) + words.drop(start + length)).joinToString(" ")
                return Heard(woke = true, command = rest.removePrefix("hey ").removePrefix("ok ").removePrefix("okay ").trim())
            }
        }
        return null
    }

    /** How many single-letter changes turn [a] into [b]. */
    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val swap = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(swap, previous[j] + 1, current[j - 1] + 1)
            }
            previous = current
        }
        return previous[b.length]
    }

    /** One wrong letter is forgiven for every this many letters of the name. */
    private const val LETTERS_PER_SLIP = 4
}

/**
 * Answers something spoken, or tapped: the assistant screen's shortcut
 * buttons send the same sentences a voice would, so a tap and a spoken
 * command end in the same place.
 */
@Singleton
class VoiceAssistant @Inject constructor(
    commands: Set<@JvmSuppressWildcards VoiceCommand>,
    private val settings: VoiceSettingsRepository,
    private val taskPreferences: TaskPreferencesRepository,
    private val undoCompletion: UndoCompletionUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val standing: StandingRemindersUseCase,
    private val torch: Torch,
) {
    private val ordered = commands.sortedBy { it.order }

    /** What the last command changed. Only the newest change can be taken back. */
    private var lastUndo: VoiceUndo? = null

    val canUndo: Boolean get() = lastUndo != null

    suspend fun answer(text: String, today: LocalDate, now: LocalTime): VoiceReply {
        val words = SpokenText.words(text)
        if (words.isEmpty()) return notUnderstood()
        if (UndoCommand.isUndo(words)) return undo()

        val context = VoiceContext(today, now, settings.settings.first(), taskPreferences.preferences.first().dayParts)
        for (command in ordered) {
            val reply = command.answer(words, context) ?: continue
            if (reply.undo != null) lastUndo = reply.undo
            return reply
        }
        return notUnderstood()
    }

    suspend fun undo(): VoiceReply {
        val undo = lastUndo ?: return VoiceReply("Nothing to undo")
        lastUndo = null
        when (undo) {
            is VoiceUndo.Completed -> undoCompletion(undo.undo)
            is VoiceUndo.Created -> deleteTask(undo.taskId)
            is VoiceUndo.Timer -> standing.remove(undo.reminder)
            is VoiceUndo.Torch -> torch.set(undo.wasOn)
        }
        return VoiceReply("Undone")
    }

    private fun notUnderstood() = VoiceReply(
        spoken = "I did not catch that",
        lines = listOf("Try: what should I do now", "Or: add buy milk tomorrow"),
        understood = false,
    )
}

/**
 * One exchange with the assistant, the same for a spoken sentence, a tapped
 * shortcut and the listening service: answer it, and read the answer aloud
 * when the user wants that.
 */
class TalkUseCase @Inject constructor(
    private val assistant: VoiceAssistant,
    private val settings: VoiceSettingsRepository,
    private val engine: SpeechEngine,
    private val nameListener: NameListener,
    private val speaker: Speaker,
    private val time: TimeSource,
    private val zone: ZoneSource,
) {
    /** Opens the microphone. See [SpeechEngine.listen]. */
    fun listen(): Flow<Speech> = engine.listen()

    /** Keeps the microphone open for the assistant's name only. See [NameListener]. */
    fun listenForName(names: List<String>): Flow<Speech> = nameListener.listen(names)

    suspend fun say(text: String): VoiceReply = spoken(assistant.answer(text, today(), now()))

    suspend fun undo(): VoiceReply = spoken(assistant.undo())

    fun stopSpeaking() = speaker.stop()

    private suspend fun spoken(reply: VoiceReply): VoiceReply {
        if (settings.settings.first().speakReplies) speaker.say(reply.speech)
        return reply
    }

    private fun moment() = Instant.ofEpochMilli(time.nowMillis()).atZone(zone.zone())
    private fun today(): LocalDate = moment().toLocalDate()
    private fun now(): LocalTime = moment().toLocalTime()
}

class VoiceSettingsUseCase @Inject constructor(private val repository: VoiceSettingsRepository) {

    fun observe(): Flow<VoiceSettings> = repository.settings

    suspend fun setWakeName(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        /** What the old name sounded like says nothing about the new one. */
        repository.update { if (it.wakeName == clean) it else it.copy(wakeName = clean, soundsLike = emptySet()) }
    }

    /** Remembers what the speech engine wrote when the user said the name. */
    suspend fun learnName(heard: String) {
        val phrase = SpokenText.normalised(heard)
        if (phrase.isEmpty() || SpokenText.words(phrase).size > MAX_NAME_WORDS) return
        repository.update { it.copy(soundsLike = it.soundsLike + phrase) }
    }

    suspend fun forgetLearntNames() = repository.update { it.copy(soundsLike = emptySet()) }

    suspend fun setListenWhen(listenWhen: ListenWhen) = repository.update { it.copy(listenWhen = listenWhen) }

    suspend fun setSpeakReplies(on: Boolean) = repository.update { it.copy(speakReplies = on) }

    suspend fun setMaxSpokenItems(count: Int) =
        repository.update { it.copy(maxSpokenItems = count.coerceIn(1, MAX_SPOKEN_ITEMS)) }

    suspend fun setListenSeconds(seconds: Int) =
        repository.update { it.copy(listenSeconds = seconds.coerceIn(MIN_LISTEN_SECONDS, MAX_LISTEN_SECONDS)) }

    private companion object {
        /** A name is a word or two; a longer phrase heard during learning is the user talking, not the name. */
        const val MAX_NAME_WORDS = 3
        const val MAX_SPOKEN_ITEMS = 20
        const val MIN_LISTEN_SECONDS = 3
        const val MAX_LISTEN_SECONDS = 30
    }
}
