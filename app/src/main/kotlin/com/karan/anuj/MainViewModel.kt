package com.karan.anuj

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.history.HouseKeepingUseCase
import com.karan.anuj.core.domain.settings.AppSettings
import com.karan.anuj.core.domain.settings.ObserveSettingsUseCase
import com.karan.anuj.core.domain.settings.UpdateSettingUseCase
import com.karan.anuj.core.domain.task.RollOverTasksUseCase
import com.karan.anuj.core.security.AppLockController
import com.karan.anuj.core.security.LockState
import com.karan.anuj.feature.place.platform.PlaceRunner
import com.karan.anuj.feature.reminder.platform.ReminderRunner
import com.karan.anuj.feature.voice.platform.VoiceRunner
import com.karan.anuj.feature.task.common.DayClock
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
    private val rollOverTasks: RollOverTasksUseCase,
    private val reminders: ReminderRunner,
    private val voice: VoiceRunner,
    private val houseKeeping: HouseKeepingUseCase,
    private val places: PlaceRunner,
) : ViewModel() {

    /** Null until the saved settings have been read, so nothing is drawn with the wrong theme or before the lock is known. */
    val state: StateFlow<RootState?> =
        combine(observeSettings(), appLock.state) { settings, lock -> RootState(settings, lock) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Unfinished tasks are carried over once for each new day: when the app
     * starts, and again at midnight if it is still open. Collecting the
     * day rather than calling this from a screen means it runs whichever
     * tab the user happens to be on.
     */
    private val clock = DayClock(viewModelScope)

    init {
        viewModelScope.launch {
            clock.day.collect { day -> rollOverTasks(day.date) }
        }
    }

    /**
     * The phone may have slept through midnight or changed time zone;
     * re-reading the day catches both. Reminders are checked too, in case an
     * alarm was held back by the phone while the app was closed.
     *
     * The phone only lets a microphone service start while the app is on
     * screen, so listening for the assistant's name is (re)started here too.
     */
    fun onForeground() {
        clock.refresh()
        reminders.syncNow()
        voice.onForeground()
        /** Where the phone is now is read once each time the app is opened, when places are switched on. */
        places.onForeground()
        /** The clean-up the user has chosen in Settings; with the defaults it removes nothing. */
        viewModelScope.launch { houseKeeping() }
    }

    fun onUnlocked() = appLock.unlock()

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { updateSetting.appLock(enabled) }
    }

    fun finishOnboarding() {
        viewModelScope.launch { updateSetting.onboardingDone(true) }
    }
}
