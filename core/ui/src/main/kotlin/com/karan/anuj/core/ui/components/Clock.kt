package com.karan.anuj.core.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ClockFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** "6:30 pm": how a clock time is written everywhere in the app. */
fun clockLabel(time: LocalTime): String = time.format(ClockFormat).lowercase(Locale.ENGLISH)

/**
 * The clock a time is picked on, so no time is ever typed. Shared by every
 * feature that asks for a time; the two button labels come from the caller
 * because this module holds no text of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockDialog(
    initial: LocalTime,
    okLabel: String,
    cancelLabel: String,
    onPicked: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onPicked(LocalTime.of(state.hour, state.minute))
                    onDismiss()
                },
            ) { Text(okLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
        text = { TimePicker(state = state) },
    )
}
