package com.karan.anuj.core.data.task

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karan.anuj.core.domain.task.CarryOverCodec
import com.karan.anuj.core.domain.task.CarryOverRule
import com.karan.anuj.core.domain.task.DayParts
import com.karan.anuj.core.domain.task.TaskPreferences
import com.karan.anuj.core.domain.task.TaskPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.taskPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "task_preferences")

/**
 * The app-wide task defaults. Kept in their own small file, separate from
 * the display settings, so the task feature does not widen the settings
 * interface every other screen depends on.
 */
@Singleton
class DataStoreTaskPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : TaskPreferencesRepository {

    override val preferences: Flow<TaskPreferences> = context.taskPreferencesStore.data
        .map { prefs ->
            val defaults = TaskPreferences()
            TaskPreferences(
                defaultCarryOver = CarryOverCodec.decode(prefs[DEFAULT_CARRY_OVER]) ?: defaults.defaultCarryOver,
                carryLimit = prefs[CARRY_LIMIT] ?: defaults.carryLimit,
                dayParts = decodeDayParts(prefs[DAY_PARTS]) ?: defaults.dayParts,
                estimateChoices = decodeMinutes(prefs[ESTIMATE_CHOICES]).ifEmpty { defaults.estimateChoices },
            )
        }
        .distinctUntilChanged()

    override suspend fun setDefaultCarryOver(rule: CarryOverRule) {
        context.taskPreferencesStore.edit { it[DEFAULT_CARRY_OVER] = CarryOverCodec.encode(rule).orEmpty() }
    }

    override suspend fun setCarryLimit(limit: Int) {
        context.taskPreferencesStore.edit { it[CARRY_LIMIT] = limit }
    }

    override suspend fun setDayParts(parts: DayParts) {
        val minutes = listOf(parts.morning, parts.afternoon, parts.evening, parts.night).map { it.hour * 60 + it.minute }
        context.taskPreferencesStore.edit { it[DAY_PARTS] = minutes.joinToString(SEPARATOR) }
    }

    override suspend fun setEstimateChoices(minutes: List<Int>) {
        context.taskPreferencesStore.edit { it[ESTIMATE_CHOICES] = minutes.joinToString(SEPARATOR) }
    }

    /** Numbers are stored as one comma-separated line; anything unreadable is skipped. */
    private fun decodeMinutes(stored: String?): List<Int> =
        stored.orEmpty().split(SEPARATOR).mapNotNull { it.trim().toIntOrNull() }

    private fun decodeDayParts(stored: String?): DayParts? {
        val times = decodeMinutes(stored)
            .filter { it in 0 until MINUTES_PER_DAY }
            .map { LocalTime.of(it / 60, it % 60) }
        return if (times.size == 4) DayParts(times[0], times[1], times[2], times[3]) else null
    }

    private companion object {
        val DEFAULT_CARRY_OVER = stringPreferencesKey("default_carry_over")
        val CARRY_LIMIT = intPreferencesKey("carry_limit")
        val DAY_PARTS = stringPreferencesKey("day_parts")
        val ESTIMATE_CHOICES = stringPreferencesKey("estimate_choices")
        const val SEPARATOR = ","
        const val MINUTES_PER_DAY = 24 * 60
    }
}
