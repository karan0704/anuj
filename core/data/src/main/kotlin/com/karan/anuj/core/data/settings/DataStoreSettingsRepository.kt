package com.karan.anuj.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karan.anuj.core.domain.settings.AppSettings
import com.karan.anuj.core.domain.settings.ColourStyle
import com.karan.anuj.core.domain.settings.HandSide
import com.karan.anuj.core.domain.settings.SettingsRepository
import com.karan.anuj.core.domain.settings.TextScale
import com.karan.anuj.core.domain.settings.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Settings live in DataStore rather than the database: they are needed before
 * the first frame (theme, lock) and must not wait for the encrypted database
 * to open.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = context.settingsStore.data
        .map { prefs ->
            AppSettings(
                themeMode = enumOr(prefs[THEME_MODE], ThemeMode.SYSTEM),
                colourStyle = enumOr(prefs[COLOUR_STYLE], ColourStyle.TEAL),
                textScale = enumOr(prefs[TEXT_SCALE], TextScale.NORMAL),
                handSide = enumOr(prefs[HAND_SIDE], HandSide.RIGHT),
                lowerLists = prefs[LOWER_LISTS] ?: true,
                appLockEnabled = prefs[APP_LOCK] ?: false,
                onboardingDone = prefs[ONBOARDING_DONE] ?: false,
                lockAfterSeconds = prefs[LOCK_AFTER_SECONDS] ?: AppSettings.DEFAULT_LOCK_AFTER_SECONDS,
                emptyTrashAfterDays = prefs[EMPTY_TRASH_DAYS],
                keepRemovedPhotosDays = prefs[KEEP_PHOTOS_DAYS] ?: 0,
                keepHistoryDays = prefs[KEEP_HISTORY_DAYS],
            )
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setColourStyle(style: ColourStyle) {
        context.settingsStore.edit { it[COLOUR_STYLE] = style.name }
    }

    override suspend fun setTextScale(scale: TextScale) {
        context.settingsStore.edit { it[TEXT_SCALE] = scale.name }
    }

    override suspend fun setHandSide(side: HandSide) {
        context.settingsStore.edit { it[HAND_SIDE] = side.name }
    }

    override suspend fun setLowerLists(lower: Boolean) {
        context.settingsStore.edit { it[LOWER_LISTS] = lower }
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[APP_LOCK] = enabled }
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        context.settingsStore.edit { it[ONBOARDING_DONE] = done }
    }

    override suspend fun setLockAfterSeconds(seconds: Int) {
        context.settingsStore.edit { it[LOCK_AFTER_SECONDS] = seconds }
    }

    override suspend fun setEmptyTrashAfterDays(days: Int?) = setOrClear(EMPTY_TRASH_DAYS, days)

    override suspend fun setKeepRemovedPhotosDays(days: Int) {
        context.settingsStore.edit { it[KEEP_PHOTOS_DAYS] = days }
    }

    override suspend fun setKeepHistoryDays(days: Int?) = setOrClear(KEEP_HISTORY_DAYS, days)

    /** "Never" and "always" are stored as the key being absent, so there is no magic number standing for them. */
    private suspend fun setOrClear(key: Preferences.Key<Int>, value: Int?) {
        context.settingsStore.edit { if (value == null) it.remove(key) else it[key] = value }
    }

    /** A value saved by a newer version of the app that this one does not know falls back to the default. */
    private inline fun <reified T : Enum<T>> enumOr(stored: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == stored } ?: default

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOUR_STYLE = stringPreferencesKey("colour_style")
        val TEXT_SCALE = stringPreferencesKey("text_scale")
        val HAND_SIDE = stringPreferencesKey("hand_side")
        val LOWER_LISTS = booleanPreferencesKey("lower_lists")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LOCK_AFTER_SECONDS = intPreferencesKey("lock_after_seconds")
        val EMPTY_TRASH_DAYS = intPreferencesKey("empty_trash_after_days")
        val KEEP_PHOTOS_DAYS = intPreferencesKey("keep_removed_photos_days")
        val KEEP_HISTORY_DAYS = intPreferencesKey("keep_history_days")
    }
}
