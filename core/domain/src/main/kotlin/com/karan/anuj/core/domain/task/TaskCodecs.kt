package com.karan.anuj.core.domain.task

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Text and number forms of the task values that are not a single column
 * type. Used for storage, for backup files and for the change history, so
 * all three always agree on what a stored value means.
 *
 * Decoding never throws: text this version does not understand (written by
 * a newer version, or damaged) reads back as "not set".
 */
object RepetitionCodec {

    fun encode(rule: Repetition?): String? = when (rule) {
        null -> null
        is Repetition.Daily -> "DAILY;${rule.every}"
        is Repetition.Weekly -> "WEEKLY;${rule.every};${rule.days.sorted().joinToString(",") { it.name }}"
        is Repetition.Monthly -> "MONTHLY;${rule.every};${rule.dayOfMonth}"
        is Repetition.Yearly -> "YEARLY;${rule.every};${rule.month};${rule.dayOfMonth}"
    }

    fun decode(text: String?): Repetition? {
        val parts = text?.split(";") ?: return null
        val every = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return when (parts[0]) {
            "DAILY" -> Repetition.Daily(every)
            "WEEKLY" -> Repetition.Weekly(
                every = every,
                days = parts.getOrNull(2).orEmpty()
                    .split(",")
                    .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
                    .toSet(),
            )
            "MONTHLY" -> parts.getOrNull(2)?.toIntOrNull()?.let { Repetition.Monthly(every, it) }
            "YEARLY" -> {
                val month = parts.getOrNull(2)?.toIntOrNull() ?: return null
                val day = parts.getOrNull(3)?.toIntOrNull() ?: return null
                Repetition.Yearly(every, month, day)
            }
            else -> null
        }
    }
}

object CarryOverCodec {

    fun encode(rule: CarryOverRule?): String? = when (rule) {
        null -> null
        CarryOverRule.NextDay -> "NEXT_DAY"
        CarryOverRule.NextWeek -> "NEXT_WEEK"
        CarryOverRule.NextMonth -> "NEXT_MONTH"
        CarryOverRule.NextYear -> "NEXT_YEAR"
        is CarryOverRule.InDays -> "IN_DAYS;${rule.days}"
        is CarryOverRule.OnDate -> "ON_DATE;${rule.date.toEpochDay()}"
        CarryOverRule.AskMe -> "ASK_ME"
        CarryOverRule.DontCarry -> "DONT_CARRY"
    }

    fun decode(text: String?): CarryOverRule? {
        val parts = text?.split(";") ?: return null
        return when (parts[0]) {
            "NEXT_DAY" -> CarryOverRule.NextDay
            "NEXT_WEEK" -> CarryOverRule.NextWeek
            "NEXT_MONTH" -> CarryOverRule.NextMonth
            "NEXT_YEAR" -> CarryOverRule.NextYear
            "IN_DAYS" -> parts.getOrNull(1)?.toIntOrNull()?.let(CarryOverRule::InDays)
            "ON_DATE" -> parts.getOrNull(1)?.toLongOrNull()?.let { CarryOverRule.OnDate(LocalDate.ofEpochDay(it)) }
            "ASK_ME" -> CarryOverRule.AskMe
            "DONT_CARRY" -> CarryOverRule.DontCarry
            else -> null
        }
    }
}

/** A set of weekdays as seven bits, Monday in the lowest. */
object WeekdaysCodec {

    fun encode(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl day.ordinal) }

    fun decode(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet()
}

/** The picture of a task that the change history compares before and after an edit. */
fun Task.asHistoryFields(): Map<String, String?> = mapOf(
    "parentId" to parentId?.value,
    "name" to name,
    "description" to description,
    "dueDate" to dueDate?.toString(),
    "dueTime" to dueTime?.toString(),
    "repetition" to RepetitionCodec.encode(repetition),
    "daysOff" to daysOff.sorted().joinToString(",") { it.name },
    "priority" to priority.name,
    "energy" to energy?.name,
    "estimatedMinutes" to estimatedMinutes?.toString(),
    "carryOver" to CarryOverCodec.encode(carryOver),
    "carryCount" to carryCount.toString(),
    "tagIds" to tagIds.map { it.value }.sorted().joinToString(","),
    "completedAt" to completedAt?.toString(),
    "missedAt" to missedAt?.toString(),
    "deletedAt" to stamps.deletedAt?.toString(),
)

/** The table name tasks are stored and recorded in history under. */
const val TASK_TABLE = "task"
