package com.karan.anuj.core.domain.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Settings held in memory, so the rules that read them are tested without a phone. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
    override suspend fun setColourStyle(style: ColourStyle) = state.update { it.copy(colourStyle = style) }
    override suspend fun setTextScale(scale: TextScale) = state.update { it.copy(textScale = scale) }
    override suspend fun setHandSide(side: HandSide) = state.update { it.copy(handSide = side) }
    override suspend fun setLowerLists(lower: Boolean) = state.update { it.copy(lowerLists = lower) }
    override suspend fun setAppLockEnabled(enabled: Boolean) = state.update { it.copy(appLockEnabled = enabled) }
    override suspend fun setOnboardingDone(done: Boolean) = state.update { it.copy(onboardingDone = done) }
    override suspend fun setLockAfterSeconds(seconds: Int) = state.update { it.copy(lockAfterSeconds = seconds) }
    override suspend fun setEmptyTrashAfterDays(days: Int?) = state.update { it.copy(emptyTrashAfterDays = days) }
    override suspend fun setKeepRemovedPhotosDays(days: Int) = state.update { it.copy(keepRemovedPhotosDays = days) }
    override suspend fun setKeepHistoryDays(days: Int?) = state.update { it.copy(keepHistoryDays = days) }
}
