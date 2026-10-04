package com.karan.anuj

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karan.anuj.core.domain.settings.ThemeMode
import com.karan.anuj.core.security.BiometricAuthenticator
import com.karan.anuj.core.security.LockState
import com.karan.anuj.core.ui.theme.AnujTheme
import com.karan.anuj.feature.reminder.platform.ReminderLinks
import com.karan.anuj.navigation.AnujApp
import com.karan.anuj.ui.lock.LockScreen
import com.karan.anuj.ui.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The app's only activity. It is a FragmentActivity because the system unlock
 * prompt needs one to attach to.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var authenticator: BiometricAuthenticator

    private val viewModel: MainViewModel by viewModels()

    /** The task a tapped reminder asked to open, until the app has opened it. */
    private var taskToOpen by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        /** Only on a fresh start: after a rotation the same intent is delivered again and must not reopen the task. */
        if (savedInstanceState == null) taskToOpen = intent.getStringExtra(ReminderLinks.EXTRA_TASK_ID)

        setContent {
            val root by viewModel.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (root?.settings?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM, null -> systemDark
            }

            /**
             * The status and navigation bar icons follow the app's own theme
             * choice rather than the phone's, otherwise picking Dark on a
             * light-mode phone would leave dark icons on a dark background.
             */
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }

            AnujTheme(
                darkTheme = dark,
                textScaleFactor = root?.settings?.textScale?.factor ?: 1f,
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val current = root
                    val lockAvailable = authenticator.isAvailable(this)
                    when {
                        current == null || current.lock == LockState.CHECKING -> Unit

                        current.lock == LockState.LOCKED -> LockScreen(onUnlockRequested = ::promptUnlock)

                        !current.settings.onboardingDone -> OnboardingScreen(
                            lockAvailable = lockAvailable,
                            onEnableLock = { onDone -> confirmThenEnableLock(onDone) },
                            onFinished = viewModel::finishOnboarding,
                        )

                        else -> AnujApp(
                            lockAvailable = lockAvailable,
                            onAppLockToggled = { enable ->
                                if (enable) confirmThenEnableLock() else viewModel.setAppLock(false)
                            },
                            onForeground = viewModel::onForeground,
                            taskToOpen = taskToOpen,
                            onTaskOpened = { taskToOpen = null },
                        )
                    }
                }
            }
        }
    }

    /** A reminder tapped while the app is already open arrives here instead of starting the app again. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        taskToOpen = intent.getStringExtra(ReminderLinks.EXTRA_TASK_ID)
    }

    private fun promptUnlock() {
        /**
         * If the phone's screen lock was removed after app lock was switched
         * on, there is nothing left to check against. Opening the app is the
         * only way to avoid shutting the user out of their own data.
         */
        if (!authenticator.isAvailable(this)) {
            viewModel.onUnlocked()
            return
        }
        authenticator.authenticate(
            activity = this,
            title = getString(R.string.lock_prompt_title),
            subtitle = getString(R.string.lock_prompt_subtitle),
            onSuccess = viewModel::onUnlocked,
        )
    }

    /** App lock is only switched on after one successful unlock, proving the user can get back in. */
    private fun confirmThenEnableLock(onDone: () -> Unit = {}) {
        authenticator.authenticate(
            activity = this,
            title = getString(R.string.lock_prompt_confirm_title),
            subtitle = getString(R.string.lock_prompt_confirm_subtitle),
            onSuccess = {
                viewModel.setAppLock(true)
                onDone()
            },
        )
    }
}
