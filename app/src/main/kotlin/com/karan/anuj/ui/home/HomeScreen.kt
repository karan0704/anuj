package com.karan.anuj.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karan.anuj.R
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.ui.components.ScreenPadding
import com.karan.anuj.feature.task.today.TodayTaskList
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val TimeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val DateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

/**
 * The current time, refreshed exactly when the minute changes rather than on
 * a fixed tick, so the clock is never up to a minute behind and the screen
 * is not redrawn every second for nothing.
 */
@Composable
private fun rememberNow(): LocalDateTime {
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            val current = LocalDateTime.now()
            value = current
            val millisIntoMinute = current.second * 1_000L + current.nano / 1_000_000L
            delay(60_000L - millisIntoMinute)
        }
    }
    return now
}

/** The places the home menu leads to. One callback each, so the screen knows nothing of routes. */
class HomeLinks(
    val search: () -> Unit,
    val reminders: () -> Unit,
    val trash: () -> Unit,
    val settings: () -> Unit,
)

/**
 * The clock, with today's tasks under it. The clock is only read, so it
 * stays at the top and never moves; search and the menu are tapped, so they
 * ride at the head of the list, down where the thumb is.
 */
@Composable
fun HomeScreen(
    snackbar: SnackbarHostState,
    onOpenTask: (TaskId) -> Unit,
    links: HomeLinks,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val go = { open: () -> Unit ->
        menuOpen = false
        open()
    }

    TodayTaskList(
        snackbar = snackbar,
        onOpenTask = onOpenTask,
        header = { Clock() },
        actions = {
            IconButton(onClick = links.search) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.home_search))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.home_menu))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.home_menu_reminders)) }, onClick = { go(links.reminders) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.home_menu_trash)) }, onClick = { go(links.trash) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, onClick = { go(links.settings) })
                }
            }
        },
        onAddReminder = links.reminders,
    )
}

@Composable
private fun Clock() {
    val now = rememberNow()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 16.dp),
    ) {
        Text(now.format(TimeFormat), style = MaterialTheme.typography.displayMedium)
        Text(
            now.format(DateFormat),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
