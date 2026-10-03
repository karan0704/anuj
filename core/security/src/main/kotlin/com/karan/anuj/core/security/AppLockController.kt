package com.karan.anuj.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.karan.anuj.core.domain.lock.AppLockPolicy
import com.karan.anuj.core.domain.settings.ObserveSettingsUseCase
import com.karan.anuj.core.domain.time.TimeSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class LockState {
    /** The lock setting has not been read yet; nothing private may be shown. */
    CHECKING,
    LOCKED,
    UNLOCKED,
}

/**
 * Holds whether the app is currently locked.
 *
 * It watches two things: the app-lock setting, and the whole app moving in and
 * out of view. [AppLockPolicy] makes the actual decision; this class only
 * feeds it the facts and publishes the result.
 */
@Singleton
class AppLockController @Inject constructor(
    private val observeSettings: ObserveSettingsUseCase,
    private val time: TimeSource,
    private val policy: AppLockPolicy,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(LockState.CHECKING)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var lockEnabled: Boolean? = null
    private var backgroundedAt: Long? = null

    /** Called once from the Application so the lock covers every entry into the app. */
    fun attach() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            observeSettings()
                .map { it.appLockEnabled }
                .distinctUntilChanged()
                .collect(::onLockSettingChanged)
        }
    }

    fun unlock() {
        _state.value = LockState.UNLOCKED
    }

    private fun onLockSettingChanged(enabled: Boolean) {
        val firstRead = lockEnabled == null
        lockEnabled = enabled
        _state.value = when {
            firstRead && policy.shouldLock(enabled, backgroundedAt = null, now = time.nowMillis()) -> LockState.LOCKED
            /**
             * Switching the lock on from Settings must not throw the user out
             * of the screen they are on; it takes effect the next time they
             * come back to the app.
             */
            else -> LockState.UNLOCKED
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = time.nowMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        val enabled = lockEnabled ?: return
        val leftAt = backgroundedAt ?: return
        if (policy.shouldLock(enabled, leftAt, time.nowMillis())) {
            _state.value = LockState.LOCKED
        }
    }
}
