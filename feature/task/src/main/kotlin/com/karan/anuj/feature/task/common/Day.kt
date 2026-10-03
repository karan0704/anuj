package com.karan.anuj.feature.task.common

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A calendar day on this phone, with the instants it starts and ends at.
 * This is where stored UTC instants meet the phone's time zone.
 */
data class Day(
    val date: LocalDate,
    val startMillis: Long,
    val endMillis: Long,
) {
    companion object {
        fun now(zone: ZoneId = ZoneId.systemDefault()): Day {
            val date = LocalDate.now(zone)
            return Day(
                date = date,
                startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
    }
}

/**
 * Keeps "today" current while a screen is open: it switches over at midnight
 * by itself, and [refresh] is called when the app comes back to the front in
 * case the phone slept through midnight or its time zone changed.
 */
class DayClock(scope: CoroutineScope) {

    private val _day = MutableStateFlow(Day.now())
    val day: StateFlow<Day> = _day.asStateFlow()

    init {
        scope.launch {
            while (true) {
                val untilMidnight = _day.value.endMillis - System.currentTimeMillis()
                delay(untilMidnight.coerceAtLeast(MIN_WAIT_MILLIS))
                refresh()
            }
        }
    }

    fun refresh() {
        _day.value = Day.now()
    }

    private companion object {
        /** Stops a clock set back across midnight from making this spin. */
        const val MIN_WAIT_MILLIS = 1_000L
    }
}
