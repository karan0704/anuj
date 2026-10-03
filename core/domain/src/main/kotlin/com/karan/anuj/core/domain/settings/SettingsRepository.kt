package com.karan.anuj.core.domain.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setTextScale(scale: TextScale)
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setOnboardingDone(done: Boolean)
    suspend fun setLockAfterSeconds(seconds: Int)
}
