package com.karan.anuj

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.settings.AppSettings
import com.karan.anuj.core.domain.settings.ObserveSettingsUseCase
import com.karan.anuj.core.domain.settings.UpdateSettingUseCase
import com.karan.anuj.core.security.AppLockController
import com.karan.anuj.core.security.LockState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What decides which of the app's top-level screens is on display. */
data class RootState(
    val settings: AppSettings,
    val lock: LockState,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSetting: UpdateSettingUseCase,
    private val appLock: AppLockController,
) : ViewModel() {

    /** Null until the saved settings have been read, so nothing is drawn with the wrong theme or before the lock is known. */
    val state: StateFlow<RootState?> =
        combine(observeSettings(), appLock.state) { settings, lock -> RootState(settings, lock) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onUnlocked() = appLock.unlock()

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { updateSetting.appLock(enabled) }
    }

    fun finishOnboarding() {
        viewModelScope.launch { updateSetting.onboardingDone(true) }
    }
}
