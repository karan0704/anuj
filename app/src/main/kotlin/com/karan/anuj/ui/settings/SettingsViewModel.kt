package com.karan.anuj.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.settings.AppSettings
import com.karan.anuj.core.domain.settings.ObserveSettingsUseCase
import com.karan.anuj.core.domain.settings.TextScale
import com.karan.anuj.core.domain.settings.ThemeMode
import com.karan.anuj.core.domain.settings.UpdateSettingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSetting: UpdateSettingUseCase,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { updateSetting.themeMode(mode) }
    }

    fun setTextScale(scale: TextScale) {
        viewModelScope.launch { updateSetting.textScale(scale) }
    }

    fun setLockAfter(seconds: Int) {
        viewModelScope.launch { updateSetting.lockAfter(seconds) }
    }
}
