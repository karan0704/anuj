package com.karan.anuj.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.settings.AppSettings
import com.karan.anuj.core.domain.settings.ColourStyle
import com.karan.anuj.core.domain.settings.HandSide
import com.karan.anuj.core.domain.settings.ObserveSettingsUseCase
import com.karan.anuj.core.domain.settings.TextScale
import com.karan.anuj.core.domain.settings.ThemeMode
import com.karan.anuj.core.domain.settings.UpdateSettingUseCase
import com.karan.anuj.feature.task.common.WriteScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSetting: UpdateSettingUseCase,
    /** A setting tapped just before leaving the screen must still be saved, so saves do not run in this screen's own scope. */
    private val writes: WriteScope,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: ThemeMode) {
        writes.launch { updateSetting.themeMode(mode) }
    }

    fun setColourStyle(style: ColourStyle) {
        writes.launch { updateSetting.colourStyle(style) }
    }

    fun setHandSide(side: HandSide) {
        writes.launch { updateSetting.handSide(side) }
    }

    fun setLowerLists(lower: Boolean) {
        writes.launch { updateSetting.lowerLists(lower) }
    }

    fun setTextScale(scale: TextScale) {
        writes.launch { updateSetting.textScale(scale) }
    }

    fun setEmptyTrashAfter(days: Int?) {
        writes.launch { updateSetting.emptyTrashAfter(days) }
    }

    fun setKeepRemovedPhotos(days: Int) {
        writes.launch { updateSetting.keepRemovedPhotos(days) }
    }

    fun setKeepHistory(days: Int?) {
        writes.launch { updateSetting.keepHistory(days) }
    }

    fun setLockAfter(seconds: Int) {
        writes.launch { updateSetting.lockAfter(seconds) }
    }
}
