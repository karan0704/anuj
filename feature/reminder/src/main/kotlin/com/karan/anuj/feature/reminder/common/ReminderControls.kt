package com.karan.anuj.feature.reminder.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.karan.anuj.core.domain.reminder.Nagging
import com.karan.anuj.core.domain.reminder.ReminderSettings
import com.karan.anuj.core.domain.reminder.ReminderStyle
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.FieldRow
import com.karan.anuj.core.ui.components.Stepper
import com.karan.anuj.feature.reminder.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The controls several reminder screens share, so "keep reminding", the style and the tone look and work the same on each. */

/** A small label above a group of chips inside a sheet. */
@Composable
fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/**
 * "Keep reminding until done": off, or one of the user's gaps, and how many
 * times. A gap this reminder already has is still offered after it has been
 * taken out of the settings.
 *
 * @param gaps the gaps offered, in minutes
 * @param fallbackTimes how many repeats to start with when it is switched on
 */
@Composable
fun NaggingPicker(nagging: Nagging?, gaps: List<Int>, fallbackTimes: Int, onChange: (Nagging?) -> Unit) {
    GroupLabel(stringResource(R.string.nag_title))
    ChoiceChips(
        options = listOf<Int?>(null) + (gaps + listOfNotNull(nagging?.everyMinutes)).distinct().sorted(),
        selected = nagging?.everyMinutes,
        label = { if (it == null) stringResource(R.string.nag_off) else stringResource(R.string.nag_every, lengthLabel(it)) },
        onSelect = { gap -> onChange(gap?.let { Nagging(it, nagging?.times ?: fallbackTimes) }) },
    )
    if (nagging != null) {
        Spacer(Modifier.height(8.dp))
        Stepper(
            value = nagging.times,
            onChange = { onChange(nagging.copy(times = it)) },
            range = ReminderSettings.NAG_TIMES_RANGE,
            lessLabel = stringResource(R.string.reminder_less),
            moreLabel = stringResource(R.string.reminder_more),
            valueText = pluralStringResource(R.plurals.nag_times, nagging.times, nagging.times),
        )
    }
}

/** Notification or full-screen alarm. */
@Composable
fun StylePicker(style: ReminderStyle, onChange: (ReminderStyle) -> Unit) {
    GroupLabel(stringResource(R.string.style_title))
    ChoiceChips(
        options = ReminderStyle.entries,
        selected = style,
        label = { stringResource(it.labelRes()) },
        onSelect = onChange,
    )
}

/**
 * A row showing the chosen tone; tapping it opens the phone's own tone
 * picker, so the app needs no access to the phone's files.
 *
 * @param toneUri null means "not chosen": [unsetLabel] says what is used then
 */
@Composable
fun ToneRow(toneUri: String?, unsetLabel: String, alarm: Boolean, onPicked: (String?) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        /** Picking the phone's default is stored as "not chosen", so it keeps following the phone. */
        onPicked(picked?.takeUnless { RingtoneManager.isDefault(it) }?.toString())
    }
    FieldRow(
        label = stringResource(R.string.tone_title),
        value = toneName(toneUri, unsetLabel),
        onClick = {
            val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                    if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION,
                )
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, toneUri?.let(Uri::parse))
            try {
                launcher.launch(picker)
            } catch (noPicker: ActivityNotFoundException) {
                /** A phone with no tone picker keeps the default tone; there is nothing to choose from. */
            }
        },
    )
}

/** The tone's own name ("Chime"), read off the main thread because it asks the phone's media store. */
@Composable
private fun toneName(toneUri: String?, unsetLabel: String): String {
    val context = LocalContext.current
    val name by produceState(initialValue = unsetLabel, toneUri, unsetLabel) {
        value = if (toneUri == null) {
            unsetLabel
        } else {
            withContext(Dispatchers.IO) {
                runCatching { RingtoneManager.getRingtone(context, Uri.parse(toneUri))?.getTitle(context) }.getOrNull()
            } ?: unsetLabel
        }
    }
    return name
}
