package com.karan.anuj.core.domain.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setColourStyle(style: ColourStyle)
    suspend fun setTextScale(scale: TextScale)
    suspend fun setHandSide(side: HandSide)
    suspend fun setLowerLists(lower: Boolean)
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setOnboardingDone(done: Boolean)
    suspend fun setLockAfterSeconds(seconds: Int)
    suspend fun setEmptyTrashAfterDays(days: Int?)
    suspend fun setKeepRemovedPhotosDays(days: Int)
    suspend fun setKeepHistoryDays(days: Int?)
}
