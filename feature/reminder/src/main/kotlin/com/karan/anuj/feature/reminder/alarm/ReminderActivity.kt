package com.karan.anuj.feature.reminder.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.reminder.AnswerReminderUseCase
import com.karan.anuj.core.domain.reminder.ReminderId
import com.karan.anuj.core.domain.reminder.ReminderSettings
import com.karan.anuj.core.domain.reminder.ReminderSettingsUseCase
import com.karan.anuj.core.domain.settings.AppSettings
import com.karan.anuj.core.domain.settings.ObserveSettingsUseCase
import com.karan.anuj.core.domain.settings.ThemeMode
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.ScreenPadding
import com.karan.anuj.core.ui.components.SecondaryButton
import com.karan.anuj.core.ui.theme.AnujTheme
import com.karan.anuj.feature.reminder.R
import com.karan.anuj.feature.reminder.common.GroupLabel
import com.karan.anuj.feature.reminder.common.lengthLabel
import com.karan.anuj.feature.reminder.platform.ReminderLinks
import com.karan.anuj.feature.reminder.platform.ReminderRunner
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @property title what the reminder is for; null until it has been read
 * @property isTask a task's reminder can also be put off until tomorrow
 * @property closed the reminder was answered, or no longer exists, and the screen should go
 */
data class AnswerUiState(
    val title: String? = null,
    val isTask: Boolean = false,
    val closed: Boolean = false,
    val reminders: ReminderSettings = ReminderSettings(),
    val app: AppSettings = AppSettings(),
)

@HiltViewModel
class ReminderAnswerViewModel @Inject constructor(
    private val answer: AnswerReminderUseCase,
    reminderSettings: ReminderSettingsUseCase,
    appSettings: ObserveSettingsUseCase,
    private val runner: ReminderRunner,
) : ViewModel() {

    private data class Shown(val id: ReminderId? = null, val title: String? = null, val isTask: Boolean = false, val closed: Boolean = false)

    private val shown = MutableStateFlow(Shown())

    val state: StateFlow<AnswerUiState> =
        combine(shown, reminderSettings.observe(), appSettings()) { now, reminderPrefs, appPrefs ->
            AnswerUiState(now.title, now.isTask, now.closed, reminderPrefs, appPrefs)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AnswerUiState())

    fun show(id: ReminderId) {
        viewModelScope.launch {
            val reminder = answer.describe(id)
            shown.value = if (reminder == null) Shown(closed = true) else Shown(id, reminder.title, reminder.isTask)
        }
    }

    fun done() = respond { answer.done(it) }

    fun snooze(minutes: Int, reason: String?) = respond { answer.snooze(it, minutes, reason) }

    fun tomorrow() = respond { answer.moveToTomorrow(it) }

    /** The answer runs where it outlives this screen, and the screen is closed only once it has been saved. */
    private fun respond(action: suspend (ReminderId) -> Unit) {
        val id = shown.value.id ?: return
        runner.launch {
            action(id)
            withContext(Dispatchers.Main) { shown.value = shown.value.copy(closed = true) }
        }
    }
}

/**
 * The screen a reminder is answered on. It opens by itself over the lock
 * screen for a full-screen alarm, and from "Later" on any notification.
 * It shows only the reminder's name and its answers, never the rest of the
 * app, so it is safe to show while the app itself is locked.
 */
@AndroidEntryPoint
class ReminderActivity : ComponentActivity() {

    private val viewModel: ReminderAnswerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        show(intent)

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.closed) { if (state.closed) finish() }

            val dark = when (state.app.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            AnujTheme(darkTheme = dark, textScaleFactor = state.app.textScale.factor) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AnswerScreen(state, onDone = viewModel::done, onSnooze = viewModel::snooze, onTomorrow = viewModel::tomorrow)
                }
            }
        }
    }

    /** A second reminder arriving while this screen is up replaces what it shows. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    /**
     * The manifest already asks for this, but Android 8.0 does not read
     * those two manifest attributes; there the older window flags do the same.
     */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    private fun show(intent: Intent?) {
        val id = intent?.getStringExtra(ReminderLinks.EXTRA_REMINDER_ID)
        if (id == null) finish() else viewModel.show(ReminderId(id))
    }

    companion object {
        fun intent(context: Context, id: ReminderId): Intent = Intent(context, ReminderActivity::class.java)
            .putExtra(ReminderLinks.EXTRA_REMINDER_ID, id.value)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

/**
 * One reminder and its answers, biggest first: "Done", then how long to be
 * left alone, then "Tomorrow". A reason for snoozing is optional and picked
 * from chips before a length is tapped.
 */
@Composable
private fun AnswerScreen(
    state: AnswerUiState,
    onDone: () -> Unit,
    onSnooze: (minutes: Int, reason: String?) -> Unit,
    onTomorrow: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(ScreenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(state.title.orEmpty(), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        PrimaryButton(text = stringResource(R.string.action_done), onClick = onDone)

        Spacer(Modifier.height(16.dp))
        Column(horizontalAlignment = Alignment.Start) {
            if (state.reminders.snoozeReasons.isNotEmpty()) {
                GroupLabel(stringResource(R.string.answer_reason))
                ChoiceChips(
                    options = state.reminders.snoozeReasons,
                    selected = reason,
                    label = { it.orEmpty() },
                    /** Tapping the chosen reason again clears it. */
                    onSelect = { reason = if (reason == it) null else it },
                )
            }
            GroupLabel(stringResource(R.string.answer_again_in))
            ChoiceChips<Int?>(
                options = state.reminders.snoozeChoices,
                selected = null,
                label = { lengthLabel(it ?: 0) },
                onSelect = { minutes -> if (minutes != null) onSnooze(minutes, reason) },
            )
        }
        if (state.isTask) {
            Spacer(Modifier.height(16.dp))
            SecondaryButton(text = stringResource(R.string.answer_tomorrow), onClick = onTomorrow)
        }
    }
}
