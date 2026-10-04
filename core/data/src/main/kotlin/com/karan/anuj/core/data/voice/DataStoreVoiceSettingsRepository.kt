package com.karan.anuj.core.data.voice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karan.anuj.core.domain.voice.ListenWhen
import com.karan.anuj.core.domain.voice.VoiceSettings
import com.karan.anuj.core.domain.voice.VoiceSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.voiceSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "voice_settings")

/**
 * The assistant's settings, in their own small file. The listening service
 * reads them with the app locked, so they must not wait on the encrypted
 * database.
 *
 * The topic words are not stored yet: they have no screen to change them,
 * so the defaults are always used.
 */
@Singleton
class DataStoreVoiceSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : VoiceSettingsRepository {

    override val settings: Flow<VoiceSettings> = context.voiceSettingsStore.data.map(::read).distinctUntilChanged()

    override suspend fun update(change: (VoiceSettings) -> VoiceSettings) {
        context.voiceSettingsStore.edit { prefs -> write(change(read(prefs)), prefs) }
    }

    private fun read(prefs: Preferences): VoiceSettings {
        val defaults = VoiceSettings()
        return VoiceSettings(
            wakeName = prefs[WAKE_NAME]?.takeIf { it.isNotBlank() } ?: defaults.wakeName,
            soundsLike = prefs[SOUNDS_LIKE] ?: defaults.soundsLike,
            listenWhen = ListenWhen.entries.firstOrNull { it.name == prefs[LISTEN_WHEN] } ?: defaults.listenWhen,
            speakReplies = prefs[SPEAK_REPLIES] ?: defaults.speakReplies,
            maxSpokenItems = prefs[MAX_SPOKEN] ?: defaults.maxSpokenItems,
            listenSeconds = prefs[LISTEN_SECONDS] ?: defaults.listenSeconds,
        )
    }

    private fun write(settings: VoiceSettings, prefs: MutablePreferences) {
        prefs[WAKE_NAME] = settings.wakeName
        prefs[SOUNDS_LIKE] = settings.soundsLike
        prefs[LISTEN_WHEN] = settings.listenWhen.name
        prefs[SPEAK_REPLIES] = settings.speakReplies
        prefs[MAX_SPOKEN] = settings.maxSpokenItems
        prefs[LISTEN_SECONDS] = settings.listenSeconds
    }

    private companion object {
        val WAKE_NAME = stringPreferencesKey("wake_name")
        val SOUNDS_LIKE = stringSetPreferencesKey("sounds_like")
        val LISTEN_WHEN = stringPreferencesKey("listen_when")
        val SPEAK_REPLIES = booleanPreferencesKey("speak_replies")
        val MAX_SPOKEN = intPreferencesKey("max_spoken_items")
        val LISTEN_SECONDS = intPreferencesKey("listen_seconds")
    }
}
