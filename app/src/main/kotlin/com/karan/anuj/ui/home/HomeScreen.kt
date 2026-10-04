package com.karan.anuj.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

/**
 * The clock, with today's tasks under it. The clock is the first item of
 * the task list, so it scrolls away with the list on a long day.
 *
 * Before phase 1 this screen had no tasks to show and drew the clock above a
 * fixed "Nothing here yet" message:
 *
 *     @Composable
 *     fun HomeScreen() {
 *         val now = rememberNow()
 *
 *         Column(
 *             modifier = Modifier
 *                 .fillMaxSize()
 *                 .padding(horizontal = 24.dp, vertical = 16.dp),
 *         ) {
 *             Text(now.format(TimeFormat), style = MaterialTheme.typography.displayMedium)
 *             Text(
 *                 now.format(DateFormat),
 *                 style = MaterialTheme.typography.titleMedium,
 *                 color = MaterialTheme.colorScheme.onSurfaceVariant,
 *             )
 *
 *             Column(
 *                 modifier = Modifier
 *                     .weight(1f)
 *                     .fillMaxWidth(),
 *                 verticalArrangement = Arrangement.Center,
 *                 horizontalAlignment = Alignment.CenterHorizontally,
 *             ) {
 *                 Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge)
 *                 Spacer(Modifier.height(8.dp))
 *                 Text(
 *                     stringResource(R.string.home_empty_body),
 *                     style = MaterialTheme.typography.bodyLarge,
 *                     color = MaterialTheme.colorScheme.onSurfaceVariant,
 *                     textAlign = TextAlign.Center,
 *                 )
 *             }
 *         }
 *     }
 */
@Composable
fun HomeScreen(
    snackbar: SnackbarHostState,
    onOpenTask: (TaskId) -> Unit,
) {
    TodayTaskList(
        snackbar = snackbar,
        onOpenTask = onOpenTask,
        header = { Clock() },
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
