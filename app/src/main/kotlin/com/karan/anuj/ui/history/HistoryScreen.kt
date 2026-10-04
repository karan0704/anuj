package com.karan.anuj.ui.history

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.R
import com.karan.anuj.core.domain.history.HistoryLine
import com.karan.anuj.core.domain.history.HistoryText
import com.karan.anuj.core.domain.history.ObserveHistoryUseCase
import com.karan.anuj.core.domain.reminder.ReminderEventKind
import com.karan.anuj.core.domain.reminder.ZoneSource
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.ScreenHeader
import com.karan.anuj.core.ui.components.ScreenPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One reminder log line, ready to draw. */
data class ReminderLine(val title: String, val kind: ReminderEventKind, val at: String)

/** One edit, ready to draw. */
data class EditLine(val subject: String, val what: String, val at: String)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeHistory: ObserveHistoryUseCase,
    zones: ZoneSource,
) : ViewModel() {

    private val zone = zones.zone()

    /** Null until the first read has come back, so the empty message is not flashed. */
    val edits: StateFlow<List<EditLine>?> =
        observeHistory.recentChanges(zone)
            .map { lines -> lines.map { it.toEdit() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reminders: StateFlow<List<ReminderLine>?> =
        observeHistory.recentReminders()
            .map { events -> events.map { ReminderLine(it.title, it.kind, HistoryText.moment(it.at, zone)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun HistoryLine.toEdit() = EditLine(subject, what, HistoryText.moment(at, zone))
}

private enum class HistoryKind(@StringRes val label: Int) {
    EDITS(R.string.history_edits),
    REMINDERS(R.string.history_reminders),
}

@StringRes
private fun ReminderEventKind.label(): Int = when (this) {
    ReminderEventKind.SHOWN -> R.string.history_event_shown
    ReminderEventKind.NAGGED -> R.string.history_event_nagged
    ReminderEventKind.HELD -> R.string.history_event_held
    ReminderEventKind.SNOOZED -> R.string.history_event_snoozed
    ReminderEventKind.DONE -> R.string.history_event_done
    ReminderEventKind.MOVED -> R.string.history_event_moved
    ReminderEventKind.SUMMARY -> R.string.history_event_summary
}

/**
 * The app's logs, to read: what was edited, and what happened to every
 * reminder. Nothing here can be changed; how long the lines are kept is set
 * on the screen this one is opened from.
 */
@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val edits by viewModel.edits.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf(HistoryKind.EDITS) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.history_title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_back),
        )
        ChoiceChips(
            options = HistoryKind.entries,
            selected = kind,
            label = { stringResource(it.label) },
            onSelect = { kind = it },
            modifier = Modifier.padding(horizontal = ScreenPadding),
        )

        val empty = when (kind) {
            HistoryKind.EDITS -> edits?.isEmpty() == true
            HistoryKind.REMINDERS -> reminders?.isEmpty() == true
        }
        if (empty) {
            Text(
                stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(ScreenPadding),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            when (kind) {
                HistoryKind.EDITS -> itemsIndexed(edits.orEmpty()) { _, line -> LogRow(line.subject, line.what, line.at) }
                HistoryKind.REMINDERS -> itemsIndexed(reminders.orEmpty()) { _, line ->
                    LogRow(line.title, stringResource(line.kind.label()), line.at)
                }
            }
        }
    }
}

@Composable
private fun LogRow(title: String, detail: String, at: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(at, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
