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
                textScale = enumOr(prefs[TEXT_SCALE], TextScale.NORMAL),
                appLockEnabled = prefs[APP_LOCK] ?: false,
                onboardingDone = prefs[ONBOARDING_DONE] ?: false,
                lockAfterSeconds = prefs[LOCK_AFTER_SECONDS] ?: AppSettings.DEFAULT_LOCK_AFTER_SECONDS,
            )
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setTextScale(scale: TextScale) {
        context.settingsStore.edit { it[TEXT_SCALE] = scale.name }
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

    /** A value saved by a newer version of the app that this one does not know falls back to the default. */
    private inline fun <reified T : Enum<T>> enumOr(stored: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == stored } ?: default

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TEXT_SCALE = stringPreferencesKey("text_scale")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LOCK_AFTER_SECONDS = intPreferencesKey("lock_after_seconds")
    }
}
