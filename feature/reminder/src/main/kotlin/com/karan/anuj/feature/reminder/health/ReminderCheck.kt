package com.karan.anuj.feature.reminder.health

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.reminder.Reminder
import com.karan.anuj.core.domain.reminder.ReminderTestUseCase
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.MinTouchTarget
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.ScreenHeader
import com.karan.anuj.core.ui.components.ScreenPadding
import com.karan.anuj.core.ui.components.SecondaryButton
import com.karan.anuj.core.ui.components.SectionTitle
import com.karan.anuj.feature.reminder.R
import com.karan.anuj.feature.reminder.platform.HealthItem
import com.karan.anuj.feature.reminder.platform.ReminderHealth
import com.karan.anuj.feature.reminder.platform.ReminderRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Where the test reminder is. */
sealed interface TestState {
    data object NotStarted : TestState
    data object Waiting : TestState

    /** @property secondsLate how long after its planned time it showed */
    data class Arrived(val secondsLate: Long) : TestState
}

/** @property delaySeconds how long from now the test is sent */
data class ReminderCheckUiState(
    val items: List<HealthItem> = emptyList(),
    val delaySeconds: Int = ReminderCheckViewModel.DELAY_CHOICES.first(),
    val test: TestState = TestState.NotStarted,
)

@HiltViewModel
class ReminderCheckViewModel @Inject constructor(
    private val health: ReminderHealth,
    private val tests: ReminderTestUseCase,
    private val runner: ReminderRunner,
) : ViewModel() {

    private val items = MutableStateFlow(health.read())
    private val delaySeconds = MutableStateFlow(DELAY_CHOICES.first())
    private val running = MutableStateFlow<Reminder?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val test = running.flatMapLatest { reminder ->
        if (reminder == null) {
            flowOf<TestState>(TestState.NotStarted)
        } else {
            tests.observeDelay(reminder).map<Long?, TestState> { late ->
                if (late == null) TestState.Waiting else TestState.Arrived(late)
            }
        }
    }

    val state: StateFlow<ReminderCheckUiState> =
        combine(items, delaySeconds, test, ::ReminderCheckUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderCheckUiState(health.read()))

    val makerTip: Int? = health.makerTip()

    fun appSettings(): Intent = health.appSettings()

    /** Read again each time the screen comes back, because the fix happens in the phone's own settings. */
    fun refresh() = items.update { health.read() }

    fun setDelay(seconds: Int) {
        delaySeconds.value = seconds
    }

    fun sendTest(title: String) {
        val delay = delaySeconds.value
        runner.launch { running.value = tests.start(title, delay) }
    }

    companion object {
        /** Ten seconds to see it work at all; a minute to close the app first and see it work from closed. */
        val DELAY_CHOICES: List<Int> = listOf(10, 60, 300)
    }
}

/**
 * Shows whether this phone will let reminders through, with the system
 * screen that fixes each problem one tap away, and sends a real test
 * reminder through the real alarm.
 */
@Composable
fun ReminderCheckScreen(
    onBack: () -> Unit,
    viewModel: ReminderCheckViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val testTitle = stringResource(R.string.test_reminder_title)
    val open = { intent: Intent ->
        try {
            context.startActivity(intent)
        } catch (missing: ActivityNotFoundException) {
            /** This phone has no such settings screen; its app page is the nearest thing. */
            context.startActivity(viewModel.appSettings())
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.check_title), onBack = onBack, backLabel = stringResource(R.string.reminder_back))
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            state.items.forEach { item -> CheckRow(item, onFix = { item.fix?.let(open) }) }

            viewModel.makerTip?.let { tip ->
                HorizontalDivider()
                SectionTitle(stringResource(R.string.maker_title))
                Text(
                    stringResource(tip),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
                TextButton(onClick = { open(viewModel.appSettings()) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.maker_open))
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.test_title))
            Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
                Text(stringResource(R.string.test_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                ChoiceChips(
                    options = ReminderCheckViewModel.DELAY_CHOICES,
                    selected = state.delaySeconds,
                    label = { delayLabel(it) },
                    onSelect = viewModel::setDelay,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (val test = state.test) {
                        TestState.NotStarted -> ""
                        TestState.Waiting -> stringResource(R.string.test_waiting)
                        is TestState.Arrived ->
                            if (test.secondsLate <= ON_TIME_SECONDS) stringResource(R.string.test_on_time)
                            else stringResource(R.string.test_late, test.secondsLate)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(text = stringResource(R.string.test_send), onClick = { viewModel.sendTest(testTitle) })
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun delayLabel(seconds: Int): String =
    if (seconds < SECONDS_PER_MINUTE) pluralStringResource(R.plurals.test_in_seconds, seconds, seconds)
    else pluralStringResource(R.plurals.test_in_minutes, seconds / SECONDS_PER_MINUTE, seconds / SECONDS_PER_MINUTE)

@Composable
private fun CheckRow(item: HealthItem, onFix: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (item.ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = stringResource(if (item.ok) R.string.check_ok else R.string.check_problem),
            tint = if (item.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(stringResource(item.check.title), style = MaterialTheme.typography.bodyLarge)
            if (!item.ok) {
                Text(
                    stringResource(item.check.problem),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!item.ok && item.fix != null) {
            SecondaryButton(text = stringResource(R.string.check_fix), onClick = onFix, modifier = Modifier.fillMaxWidth(FIX_BUTTON_SHARE))
        }
    }
}

private const val SECONDS_PER_MINUTE = 60

/** A reminder this close to its planned time counts as on time; the phone is allowed a few seconds. */
private const val ON_TIME_SECONDS = 10L

/** The "Allow" button takes about a third of the row, leaving the rest to the explanation. */
private const val FIX_BUTTON_SHARE = 0.34f
