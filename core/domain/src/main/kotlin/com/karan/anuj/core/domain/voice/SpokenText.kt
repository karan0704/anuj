package com.karan.anuj.core.domain.voice

import com.karan.anuj.core.domain.task.DayParts
import com.karan.anuj.core.domain.task.Priority
import com.karan.anuj.core.domain.task.TaskDraft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * What the speech engine heard, reduced to plain lower-case words.
 *
 * An offline engine writes everything as words and splits "pm" into "p m",
 * so those are put back together here, once, and every rule that reads
 * speech starts from the same form.
 */
object SpokenText {

    fun words(text: String): List<String> {
        val raw = text.lowercase()
            .replace("p.m.", "pm").replace("a.m.", "am")
            .replace("o'clock", "oclock").replace("’", "'")
            .split(Regex("[^\\p{L}\\p{Nd}:']+"))
            .filter { it.isNotEmpty() }
        val joined = mutableListOf<String>()
        var index = 0
        while (index < raw.size) {
            val word = raw[index]
            val next = raw.getOrNull(index + 1)
            when {
                (word == "p" || word == "a") && next == "m" -> { joined += word + "m"; index += 2 }
                word == "o" && next == "clock" -> { joined += "oclock"; index += 2 }
                else -> { joined += word; index += 1 }
            }
        }
        return joined
    }

    fun normalised(text: String): String = words(text).joinToString(" ")

    private val UNITS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "ninety" to 90)

    /**
     * A number at [index], written as digits or as words up to ninety-nine.
     * @return the value and how many words it took, or null when there is no number there
     */
    fun numberAt(words: List<String>, index: Int): Pair<Int, Int>? {
        val word = words.getOrNull(index) ?: return null
        word.toIntOrNull()?.let { return it to 1 }
        if (word == "a" || word == "an") return null
        val unit = UNITS.indexOf(word)
        if (unit >= 0) return unit to 1
        val tens = TENS[word] ?: return null
        val following = UNITS.indexOf(words.getOrNull(index + 1) ?: "")
        return if (following in 1..9) (tens + following) to 2 else tens to 1
    }
}

/**
 * Turns a spoken sentence into a task: "call mum tomorrow at six pm" becomes
 * the task "Call mum", due tomorrow at 18:00.
 *
 * Words that were understood as a day, a time or a priority are taken out of
 * the name; everything else is kept as it was said, so nothing is lost when
 * a phrase is not recognised.
 */
object SpokenTaskParser {

    private val LEAD_INS = listOf(
        listOf("add", "a", "task", "to"), listOf("add", "a", "task"), listOf("add", "task"), listOf("new", "task"),
        listOf("create", "a", "task"), listOf("create", "task"), listOf("remind", "me", "to"), listOf("remind", "me"),
        listOf("i", "need", "to"), listOf("i", "have", "to"), listOf("add"),
    )
    private val DAYS = DayOfWeek.entries.associateBy { it.name.lowercase() }
    private val TRAILING = setOf("at", "on", "by", "in", "the", "for", "and", "this")
    private const val LAST_AFTERNOON_HOUR = 7

    fun parse(text: String, today: LocalDate, dayParts: DayParts = DayParts()): TaskDraft {
        val words = SpokenText.words(text).toMutableList()
        LEAD_INS.firstOrNull { words.take(it.size) == it }?.let { repeat(it.size) { words.removeAt(0) } }

        val priority = takePriority(words)
        var date = takeDate(words, today)
        var time = takeClockTime(words) ?: takeDayPart(words, dayParts, bareWordCounts = date != null)
        if (takePhrase(words, listOf("tonight"))) {
            date = date ?: today
            time = time ?: dayParts.night
        }
        if (time != null && date == null) date = today

        while (words.isNotEmpty() && words.last() in TRAILING) words.removeAt(words.lastIndex)
        val name = words.joinToString(" ").replaceFirstChar { it.uppercase() }
        return TaskDraft(name = name, dueDate = date, dueTime = time, priority = priority)
    }

    private fun takePhrase(words: MutableList<String>, phrase: List<String>): Boolean {
        val at = (0..words.size - phrase.size).firstOrNull { words.subList(it, it + phrase.size) == phrase } ?: return false
        repeat(phrase.size) { words.removeAt(at) }
        return true
    }

    private fun takePriority(words: MutableList<String>): Priority = when {
        takePhrase(words, listOf("high", "priority")) || takePhrase(words, listOf("urgent")) ||
            takePhrase(words, listOf("important")) -> Priority.HIGH
        takePhrase(words, listOf("low", "priority")) -> Priority.LOW
        else -> Priority.NONE
    }

    private fun takeDate(words: MutableList<String>, today: LocalDate): LocalDate? {
        if (takePhrase(words, listOf("day", "after", "tomorrow"))) return today.plusDays(2)
        if (takePhrase(words, listOf("tomorrow"))) return today.plusDays(1)
        if (takePhrase(words, listOf("today"))) return today
        if (takePhrase(words, listOf("next", "week"))) return today.plusWeeks(1)
        if (takePhrase(words, listOf("next", "month"))) return today.plusMonths(1)
        val at = words.indexOfFirst { it in DAYS }
        if (at < 0) return null
        val day = DAYS.getValue(words[at])
        words.removeAt(at)
        if (at > 0 && words[at - 1] in setOf("on", "next", "this")) words.removeAt(at - 1)
        return today.with(TemporalAdjusters.next(day))
    }

    /**
     * A clock time: "at six", "six pm", "6:30 pm", "six thirty", "at eighteen thirty".
     * Without "am" or "pm", an hour from one to seven is taken as the afternoon,
     * because nobody means 3 in the night when they say "at three".
     */
    private fun takeClockTime(words: MutableList<String>): LocalTime? {
        if (takePhrase(words, listOf("at", "noon")) || takePhrase(words, listOf("noon"))) return LocalTime.NOON
        if (takePhrase(words, listOf("at", "midnight")) || takePhrase(words, listOf("midnight"))) return LocalTime.MIDNIGHT
        for (start in words.indices) {
            val saidAt = words[start] == "at"
            val from = if (saidAt) start + 1 else start
            val read = readTime(words, from) ?: continue
            if (!saidAt && !read.explicit) continue
            repeat(read.length + (from - start)) { words.removeAt(start) }
            return read.time
        }
        return null
    }

    private data class ReadTime(val time: LocalTime, val length: Int, val explicit: Boolean)

    private fun readTime(words: List<String>, from: Int): ReadTime? {
        val first = words.getOrNull(from) ?: return null
        var index = from
        var hour: Int
        var minute = 0
        var hadColon = false
        if (':' in first) {
            val parts = first.split(':')
            hour = parts[0].toIntOrNull() ?: return null
            minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
            hadColon = true
            index += 1
        } else {
            val (value, length) = SpokenText.numberAt(words, index) ?: return null
            hour = value
            index += length
            SpokenText.numberAt(words, index)?.let { (maybeMinute, minuteLength) ->
                if (maybeMinute in 0..59 && minuteLength > 0 && words.getOrNull(index) != "oclock") {
                    minute = maybeMinute
                    index += minuteLength
                }
            }
        }
        if (words.getOrNull(index) == "oclock") index += 1
        val meridiem = words.getOrNull(index)?.takeIf { it == "am" || it == "pm" }
        if (meridiem != null) index += 1
        if (hour !in 0..23 || minute !in 0..59) return null
        hour = when {
            meridiem == "pm" && hour < 12 -> hour + 12
            meridiem == "am" && hour == 12 -> 0
            meridiem == null && hour in 1..LAST_AFTERNOON_HOUR -> hour + 12
            else -> hour
        }
        return ReadTime(LocalTime.of(hour, minute), index - from, explicit = meridiem != null || hadColon)
    }

    /**
     * @param bareWordCounts "morning" on its own is a time only when a day was
     * also said ("tomorrow morning"); otherwise it stays in the name, so
     * "do the morning routine" keeps its name.
     */
    private fun takeDayPart(words: MutableList<String>, parts: DayParts, bareWordCounts: Boolean): LocalTime? {
        val named = listOf(
            "morning" to parts.morning, "afternoon" to parts.afternoon, "evening" to parts.evening, "night" to parts.night,
        )
        for ((word, time) in named) {
            if (takePhrase(words, listOf("in", "the", word)) || takePhrase(words, listOf("at", word)) ||
                takePhrase(words, listOf("this", word)) || (bareWordCounts && takePhrase(words, listOf(word)))
            ) {
                return time
            }
        }
        return null
    }
}
