package com.karan.anuj.core.data.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karan.anuj.core.domain.reminder.CategorySettings
import com.karan.anuj.core.domain.reminder.Delivery
import com.karan.anuj.core.domain.reminder.Nagging
import com.karan.anuj.core.domain.reminder.QuietHours
import com.karan.anuj.core.domain.reminder.ReminderCategory
import com.karan.anuj.core.domain.reminder.ReminderSettings
import com.karan.anuj.core.domain.reminder.ReminderSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.reminderSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "reminder_settings")

/**
 * The reminder settings, in their own small file like the task defaults.
 * They are read by the alarm receiver with the app closed, so they must not
 * wait on the encrypted database.
 *
 * Anything missing or unreadable falls back to the default for that one
 * value, so a setting added by a later version never breaks the rest.
 */
@Singleton
class DataStoreReminderSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderSettingsRepository {

    override val settings: Flow<ReminderSettings> = context.reminderSettingsStore.data.map(::read).distinctUntilChanged()

    override suspend fun update(change: (ReminderSettings) -> ReminderSettings) {
        context.reminderSettingsStore.edit { prefs -> write(change(read(prefs)), prefs) }
    }

    private fun read(prefs: Preferences): ReminderSettings {
        val defaults = ReminderSettings()
        return ReminderSettings(
            categories = ReminderCategory.entries.associateWith { readCategory(prefs, it) },
            quietHours = QuietHours(
                enabled = prefs[QUIET_ON] ?: defaults.quietHours.enabled,
                from = prefs[QUIET_FROM]?.toClockTime() ?: defaults.quietHours.from,
                until = prefs[QUIET_UNTIL]?.toClockTime() ?: defaults.quietHours.until,
            ),
            dailyLimit = prefs[DAILY_LIMIT] ?: defaults.dailyLimit,
            calmMode = prefs[CALM_MODE] ?: defaults.calmMode,
            summaryTimes = prefs[SUMMARY_TIMES]?.toInts()?.mapNotNull { it.toClockTime() } ?: defaults.summaryTimes,
            allDayTime = prefs[ALL_DAY_TIME]?.toClockTime() ?: defaults.allDayTime,
            autoRemindTimedTasks = prefs[AUTO_REMIND] ?: defaults.autoRemindTimedTasks,
            defaultNagging = when (val every = prefs[NAG_EVERY]) {
                null -> defaults.defaultNagging
                NAG_OFF -> null
                else -> Nagging(every, prefs[NAG_TIMES] ?: DEFAULT_NAG_TIMES)
            },
            leadChoices = prefs[LEAD_CHOICES]?.toInts()?.ifEmpty { null } ?: defaults.leadChoices,
            snoozeChoices = prefs[SNOOZE_CHOICES]?.toInts()?.ifEmpty { null } ?: defaults.snoozeChoices,
            defaultSnoozeMinutes = prefs[SNOOZE_DEFAULT] ?: defaults.defaultSnoozeMinutes,
            nagChoices = prefs[NAG_CHOICES]?.toInts()?.ifEmpty { null } ?: defaults.nagChoices,
            snoozeReasons = prefs[SNOOZE_REASONS]?.split(LINE)?.filter { it.isNotBlank() } ?: defaults.snoozeReasons,
            routineWarningMinutes = prefs[ROUTINE_WARNING] ?: defaults.routineWarningMinutes,
        )
    }

    private fun write(settings: ReminderSettings, prefs: MutablePreferences) {
        ReminderCategory.entries.forEach { writeCategory(prefs, it, settings.category(it)) }
        prefs[QUIET_ON] = settings.quietHours.enabled
        prefs[QUIET_FROM] = settings.quietHours.from.toMinuteOfDay()
        prefs[QUIET_UNTIL] = settings.quietHours.until.toMinuteOfDay()
        prefs[DAILY_LIMIT] = settings.dailyLimit
        prefs[CALM_MODE] = settings.calmMode
        prefs[SUMMARY_TIMES] = settings.summaryTimes.joinToString(COMMA) { it.toMinuteOfDay().toString() }
        prefs[ALL_DAY_TIME] = settings.allDayTime.toMinuteOfDay()
        prefs[AUTO_REMIND] = settings.autoRemindTimedTasks
        prefs[NAG_EVERY] = settings.defaultNagging?.everyMinutes ?: NAG_OFF
        prefs[NAG_TIMES] = settings.defaultNagging?.times ?: DEFAULT_NAG_TIMES
        prefs[LEAD_CHOICES] = settings.leadChoices.joinToString(COMMA)
        prefs[SNOOZE_CHOICES] = settings.snoozeChoices.joinToString(COMMA)
        prefs[SNOOZE_DEFAULT] = settings.defaultSnoozeMinutes
        prefs[NAG_CHOICES] = settings.nagChoices.joinToString(COMMA)
        prefs[SNOOZE_REASONS] = settings.snoozeReasons.joinToString(LINE)
        prefs[ROUTINE_WARNING] = settings.routineWarningMinutes
    }

    private fun readCategory(prefs: Preferences, category: ReminderCategory): CategorySettings {
        val defaults = category.defaults
        val key = category.name.lowercase()
        return CategorySettings(
            enabled = prefs[booleanPreferencesKey("${key}_enabled")] ?: defaults.enabled,
            delivery = Delivery.entries.firstOrNull { it.name == prefs[stringPreferencesKey("${key}_delivery")] } ?: defaults.delivery,
            toneUri = prefs[stringPreferencesKey("${key}_tone")]?.takeIf { it.isNotEmpty() },
            vibrate = prefs[booleanPreferencesKey("${key}_vibrate")] ?: defaults.vibrate,
            breaksQuiet = prefs[booleanPreferencesKey("${key}_breaks_quiet")] ?: defaults.breaksQuiet,
            countsTowardLimit = prefs[booleanPreferencesKey("${key}_counts")] ?: defaults.countsTowardLimit,
        )
    }

    private fun writeCategory(prefs: MutablePreferences, category: ReminderCategory, settings: CategorySettings) {
        val key = category.name.lowercase()
        prefs[booleanPreferencesKey("${key}_enabled")] = settings.enabled
        prefs[stringPreferencesKey("${key}_delivery")] = settings.delivery.name
        prefs[stringPreferencesKey("${key}_tone")] = settings.toneUri.orEmpty()
        prefs[booleanPreferencesKey("${key}_vibrate")] = settings.vibrate
        prefs[booleanPreferencesKey("${key}_breaks_quiet")] = settings.breaksQuiet
        prefs[booleanPreferencesKey("${key}_counts")] = settings.countsTowardLimit
    }

    private fun String.toInts(): List<Int> = split(COMMA).mapNotNull { it.trim().toIntOrNull() }

    private fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

    private fun Int.toClockTime(): LocalTime? =
        if (this in 0 until MINUTES_PER_DAY) LocalTime.of(this / 60, this % 60) else null

    private companion object {
        val QUIET_ON = booleanPreferencesKey("quiet_on")
        val QUIET_FROM = intPreferencesKey("quiet_from")
        val QUIET_UNTIL = intPreferencesKey("quiet_until")
        val DAILY_LIMIT = intPreferencesKey("daily_limit")
        val CALM_MODE = booleanPreferencesKey("calm_mode")
        val SUMMARY_TIMES = stringPreferencesKey("summary_times")
        val ALL_DAY_TIME = intPreferencesKey("all_day_time")
        val AUTO_REMIND = booleanPreferencesKey("auto_remind")
        val NAG_EVERY = intPreferencesKey("nag_every")
        val NAG_TIMES = intPreferencesKey("nag_times")
        val LEAD_CHOICES = stringPreferencesKey("lead_choices")
        val SNOOZE_CHOICES = stringPreferencesKey("snooze_choices")
        val SNOOZE_DEFAULT = intPreferencesKey("snooze_default")
        val NAG_CHOICES = stringPreferencesKey("nag_choices")
        val SNOOZE_REASONS = stringPreferencesKey("snooze_reasons")
        val ROUTINE_WARNING = intPreferencesKey("routine_warning")

        const val COMMA = ","
        const val LINE = "\n"
        const val MINUTES_PER_DAY = 24 * 60

        /** Stored in place of a gap when automatic reminders do not repeat. */
        const val NAG_OFF = 0
        const val DEFAULT_NAG_TIMES = 3
    }
}
