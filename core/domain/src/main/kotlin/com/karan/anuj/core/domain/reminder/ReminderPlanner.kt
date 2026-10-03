package com.karan.anuj.core.domain.reminder

/** The next moment a reminder should show, and why. */
sealed interface PlannedFire {
    val at: Long

    /** A planned time has come. */
    data class Occurrence(override val at: Long) : PlannedFire

    /** It showed, was not answered, and is due to repeat. */
    data class Nag(override val at: Long) : PlannedFire

    /** The user asked to be reminded again at this moment. */
    data class SnoozeOver(override val at: Long) : PlannedFire
}

/**
 * Works out when a reminder is next due. Pure arithmetic on what is stored:
 * given the same reminder, task and settings it always gives the same answer,
 * so it can be asked again at any time (after a restart, a time-zone change,
 * an edit) without a reminder being lost or doubled.
 */
object ReminderPlanner {

    fun next(reminder: Reminder, context: ScheduleContext): PlannedFire? {
        if (!reminder.isLive(context)) return null
        val state = reminder.state
        state.snoozedUntil?.let { return PlannedFire.SnoozeOver(it) }

        val dealtWithUntil = state.lastOccurrenceAt ?: reminder.stamps.createdAt
        val occurrence = reminder.schedule.nextOccurrence(after = dealtWithUntil, context)?.let(PlannedFire::Occurrence)

        /**
         * A task reminder with a planned time still ahead means the task has
         * moved on since it last showed (a repeating task was ticked, or the
         * task was carried to another day), so the old showing is not repeated.
         */
        if (!reminder.isStanding && occurrence != null) return occurrence

        val nag = nagAt(reminder)?.let(PlannedFire::Nag)
        return listOfNotNull(occurrence, nag).minByOrNull { it.at }
    }

    /** Switched on, not in the trash, and (for a task's reminder) the task is still open. */
    private fun Reminder.isLive(context: ScheduleContext): Boolean {
        if (!enabled || stamps.isDeleted) return false
        return isStanding || context.task?.isOpen == true
    }

    private fun nagAt(reminder: Reminder): Long? {
        val nagging = reminder.nagging ?: return null
        val state = reminder.state
        val firedAt = state.lastFiredAt ?: return null
        if (!state.isWaitingForAnswer || state.nagsSent >= nagging.times) return null
        return firedAt + nagging.everyMinutes * MILLIS_PER_MINUTE
    }

    const val MILLIS_PER_MINUTE = 60_000L
}
