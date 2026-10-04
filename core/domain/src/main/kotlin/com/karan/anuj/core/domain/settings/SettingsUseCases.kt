package com.karan.anuj.core.domain.settings

import com.karan.anuj.core.domain.history.ChangeHistoryRepository
import com.karan.anuj.core.domain.history.diffFields
import com.karan.anuj.core.domain.time.TimeSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** The settings are stored under this table and row name in the change history. */
private const val SETTINGS_TABLE = "settings"
private const val SETTINGS_ROW = "app"

class ObserveSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<AppSettings> = repository.settings
}

/**
 * Applies one settings change and writes what the setting was before into the
 * change history, the same way an edit to any other record is tracked.
 */
class UpdateSettingUseCase @Inject constructor(
    private val repository: SettingsRepository,
    private val history: ChangeHistoryRepository,
    private val time: TimeSource,
) {
    suspend fun themeMode(mode: ThemeMode) = tracked { repository.setThemeMode(mode) }

    suspend fun colourStyle(style: ColourStyle) = tracked { repository.setColourStyle(style) }

    suspend fun textScale(scale: TextScale) = tracked { repository.setTextScale(scale) }

    suspend fun handSide(side: HandSide) = tracked { repository.setHandSide(side) }

    suspend fun lowerLists(lower: Boolean) = tracked { repository.setLowerLists(lower) }

    suspend fun appLock(enabled: Boolean) = tracked { repository.setAppLockEnabled(enabled) }

    suspend fun onboardingDone(done: Boolean) = tracked { repository.setOnboardingDone(done) }

    suspend fun lockAfter(seconds: Int) = tracked { repository.setLockAfterSeconds(seconds.coerceAtLeast(0)) }

    private suspend fun tracked(change: suspend () -> Unit) {
        val before = repository.settings.first()
        change()
        val after = repository.settings.first()
        val changes = diffFields(
            table = SETTINGS_TABLE,
            rowId = SETTINGS_ROW,
            before = before.asFields(),
            after = after.asFields(),
            changedAt = time.nowMillis(),
        )
        if (changes.isNotEmpty()) history.record(changes)
    }

    private fun AppSettings.asFields(): Map<String, String?> = mapOf(
        "themeMode" to themeMode.name,
        "colourStyle" to colourStyle.name,
        "textScale" to textScale.name,
        "handSide" to handSide.name,
        "lowerLists" to lowerLists.toString(),
        "appLockEnabled" to appLockEnabled.toString(),
        "onboardingDone" to onboardingDone.toString(),
        "lockAfterSeconds" to lockAfterSeconds.toString(),
    )
}
