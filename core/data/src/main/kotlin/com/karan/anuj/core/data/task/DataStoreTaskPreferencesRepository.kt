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
import com.karan.anuj.core.domain.task.TaskPreferences
import com.karan.anuj.core.domain.task.TaskPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
            )
        }
        .distinctUntilChanged()

    override suspend fun setDefaultCarryOver(rule: CarryOverRule) {
        context.taskPreferencesStore.edit { it[DEFAULT_CARRY_OVER] = CarryOverCodec.encode(rule).orEmpty() }
    }

    override suspend fun setCarryLimit(limit: Int) {
        context.taskPreferencesStore.edit { it[CARRY_LIMIT] = limit }
    }

    private companion object {
        val DEFAULT_CARRY_OVER = stringPreferencesKey("default_carry_over")
        val CARRY_LIMIT = intPreferencesKey("carry_limit")
    }
}
