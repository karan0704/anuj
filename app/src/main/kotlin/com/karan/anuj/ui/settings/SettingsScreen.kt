package com.karan.anuj.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karan.anuj.R
import com.karan.anuj.core.domain.settings.AppSettings
import com.karan.anuj.core.domain.settings.ColourStyle
import com.karan.anuj.core.domain.settings.HandSide
import com.karan.anuj.core.domain.settings.TextScale
import com.karan.anuj.core.domain.settings.ThemeMode
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.FieldRow
import com.karan.anuj.core.ui.components.ScreenHeader
import com.karan.anuj.core.ui.components.SwitchRow
import com.karan.anuj.feature.reminder.settings.ReminderSettingsRows
import com.karan.anuj.feature.task.settings.TaskSettingsRows

/**
 * The branches of the settings tree. The first screen lists only these,
 * each with a line saying what is inside, and every branch opens a screen of
 * its own, so no screen ever shows more than one subject.
 */
enum class SettingsSection(val route: String, @StringRes val title: Int, @StringRes val summary: Int) {
    DISPLAY("settings/display", R.string.settings_section_display, R.string.settings_summary_display),
    TASKS("settings/tasks", R.string.settings_section_tasks, R.string.settings_summary_tasks),
    REMINDERS("settings/reminders", R.string.settings_section_reminders, R.string.settings_summary_reminders),
    PLACES("settings/places", R.string.settings_section_places, R.string.settings_summary_places),
    VOICE("settings/voice", R.string.settings_section_voice, R.string.settings_summary_voice),
    LOCK("settings/lock", R.string.settings_section_lock, R.string.settings_summary_lock),
    BACKUP("settings/backup", R.string.settings_section_backup, R.string.settings_summary_backup),
    STORAGE("settings/storage", R.string.settings_section_storage, R.string.settings_summary_storage),
}

/** The root of the settings tree: one row per branch and nothing else. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (SettingsSection) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_back),
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SettingsSection.entries.forEach { section ->
                FieldRow(
                    label = stringResource(section.summary),
                    value = stringResource(section.title),
                    onClick = { onOpen(section) },
                    valueFirst = true,
                )
                HorizontalDivider()
            }
        }
    }
}

/**
 * One branch of the tree. Each feature draws its own rows; this screen only
 * gives them a title and a way back, so adding a feature's settings never
 * means editing another feature's.
 *
 * @param lockAvailable false when the phone has no screen lock to check against
 * @param onAppLockToggled handled by the activity, because switching the lock
 * on first shows the system unlock prompt
 * @param onOpenNotifications, onOpenRegularReminders, onOpenReminderCheck open
 * the reminder screens one level further down
 * @param onOpenHistory opens the screen that shows the logs
 */
@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    onBack: () -> Unit,
    lockAvailable: Boolean,
    onAppLockToggled: (Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenRegularReminders: () -> Unit,
    onOpenReminderCheck: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(section.title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_back),
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            when (section) {
                SettingsSection.DISPLAY -> DisplaySettingsRows()
                SettingsSection.TASKS -> TaskSettingsRows()
                SettingsSection.REMINDERS -> ReminderSettingsRows(
                    onOpenNotifications = onOpenNotifications,
                    onOpenRegular = onOpenRegularReminders,
                    onOpenCheck = onOpenReminderCheck,
                )
                SettingsSection.LOCK -> LockSettingsRows(lockAvailable, onAppLockToggled)
                SettingsSection.BACKUP -> BackupSettingsRows()
                SettingsSection.STORAGE -> StorageSettingsRows(onOpenHistory)
                /** Places and voice each have a full screen of their own in their feature; the root rows open those directly. */
                SettingsSection.PLACES, SettingsSection.VOICE -> Unit
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
private fun ColourStyle.labelRes(): Int = when (this) {
    ColourStyle.TEAL -> R.string.colours_teal
    ColourStyle.IVORY -> R.string.colours_ivory
}

@StringRes
private fun TextScale.labelRes(): Int = when (this) {
    TextScale.SMALL -> R.string.text_small
    TextScale.NORMAL -> R.string.text_normal
    TextScale.LARGE -> R.string.text_large
    TextScale.EXTRA_LARGE -> R.string.text_extra_large
}

@StringRes
private fun HandSide.labelRes(): Int = when (this) {
    HandSide.RIGHT -> R.string.hand_right
    HandSide.LEFT -> R.string.hand_left
}

/** Which choice sheet is open. Saved across rotation so the sheet does not vanish mid-choice. */
private enum class DisplaySheet { NONE, THEME, COLOURS, TEXT_SIZE, HAND }

/** How the app looks and how it is laid out for the hand. Every value is a chip or a switch. */
@Composable
private fun DisplaySettingsRows(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(DisplaySheet.NONE) }
    val close = { sheet = DisplaySheet.NONE }

    FieldRow(stringResource(R.string.settings_theme), stringResource(settings.themeMode.labelRes()), { sheet = DisplaySheet.THEME })
    FieldRow(stringResource(R.string.settings_colours), stringResource(settings.colourStyle.labelRes()), { sheet = DisplaySheet.COLOURS })
    FieldRow(stringResource(R.string.settings_text_size), stringResource(settings.textScale.labelRes()), { sheet = DisplaySheet.TEXT_SIZE })
    FieldRow(stringResource(R.string.settings_hand), stringResource(settings.handSide.labelRes()), { sheet = DisplaySheet.HAND })
    SwitchRow(
        label = stringResource(R.string.settings_lower_lists),
        detail = stringResource(R.string.settings_lower_lists_detail),
        checked = settings.lowerLists,
        onChange = viewModel::setLowerLists,
    )

    when (sheet) {
        DisplaySheet.NONE -> Unit

        DisplaySheet.THEME -> AnujBottomSheet(onDismiss = close, title = stringResource(R.string.settings_theme)) {
            ChoiceChips(
                options = ThemeMode.entries,
                selected = settings.themeMode,
                label = { stringResource(it.labelRes()) },
                onSelect = viewModel::setThemeMode,
            )
        }

        DisplaySheet.COLOURS -> AnujBottomSheet(onDismiss = close, title = stringResource(R.string.settings_colours)) {
            /** The sheet is drawn in the app's theme, so a tapped chip shows its colours at once. */
            ChoiceChips(
                options = ColourStyle.entries,
                selected = settings.colourStyle,
                label = { stringResource(it.labelRes()) },
                onSelect = viewModel::setColourStyle,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.settings_colours_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DisplaySheet.TEXT_SIZE -> AnujBottomSheet(onDismiss = close, title = stringResource(R.string.settings_text_size)) {
            ChoiceChips(
                options = TextScale.entries,
                selected = settings.textScale,
                label = { stringResource(it.labelRes()) },
                onSelect = viewModel::setTextScale,
            )
            Spacer(Modifier.height(16.dp))
            /** Drawn inside the themed app, so it shows the chosen size as soon as a chip is tapped. */
            Text(stringResource(R.string.settings_text_size_preview), style = MaterialTheme.typography.bodyLarge)
        }

        DisplaySheet.HAND -> AnujBottomSheet(onDismiss = close, title = stringResource(R.string.settings_hand)) {
            ChoiceChips(
                options = HandSide.entries,
                selected = settings.handSide,
                label = { stringResource(it.labelRes()) },
                onSelect = viewModel::setHandSide,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.settings_hand_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Straight away", "30 seconds", "5 minutes": a delay in the words a person would use. */
@Composable
private fun lockAfterLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.lock_after_now)
    seconds < SECONDS_PER_MINUTE -> stringResource(R.string.lock_after_seconds, seconds)
    else -> stringResource(R.string.lock_after_minutes, seconds / SECONDS_PER_MINUTE)
}

private const val SECONDS_PER_MINUTE = 60

@Composable
private fun LockSettingsRows(
    lockAvailable: Boolean,
    onAppLockToggled: (Boolean) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var choosingDelay by rememberSaveable { mutableStateOf(false) }

    /** Still switchable off when the screen lock has gone, so the setting cannot get stuck on. */
    if (lockAvailable || settings.appLockEnabled) {
        SwitchRow(
            label = stringResource(R.string.settings_app_lock),
            detail = stringResource(if (settings.appLockEnabled) R.string.settings_app_lock_on else R.string.settings_app_lock_off),
            checked = settings.appLockEnabled,
            onChange = onAppLockToggled,
        )
    } else {
        FieldRow(stringResource(R.string.settings_app_lock), stringResource(R.string.settings_app_lock_unavailable), onClick = {})
    }
    FieldRow(stringResource(R.string.settings_lock_after), lockAfterLabel(settings.lockAfterSeconds), { choosingDelay = true })

    if (choosingDelay) {
        AnujBottomSheet(onDismiss = { choosingDelay = false }, title = stringResource(R.string.settings_lock_after)) {
            ChoiceChips(
                /** A delay saved by another version of the app still shows as the selected chip. */
                options = (AppSettings.LOCK_AFTER_CHOICES + settings.lockAfterSeconds).distinct().sorted(),
                selected = settings.lockAfterSeconds,
                label = { lockAfterLabel(it) },
                onSelect = viewModel::setLockAfter,
            )
        }
    }
}

/** "Never", "1 week", "3 months": a number of days in the words a person would use. */
@Composable
private fun daysLabel(days: Int?, @StringRes whenNull: Int, @StringRes whenZero: Int = whenNull): String = when {
    days == null -> stringResource(whenNull)
    days <= 0 -> stringResource(whenZero)
    days % DAYS_PER_YEAR == 0 -> pluralStringResource(R.plurals.keep_years, days / DAYS_PER_YEAR, days / DAYS_PER_YEAR)
    days % DAYS_PER_MONTH == 0 -> pluralStringResource(R.plurals.keep_months, days / DAYS_PER_MONTH, days / DAYS_PER_MONTH)
    days % DAYS_PER_WEEK == 0 -> pluralStringResource(R.plurals.keep_weeks, days / DAYS_PER_WEEK, days / DAYS_PER_WEEK)
    else -> pluralStringResource(R.plurals.keep_days, days, days)
}

private const val DAYS_PER_WEEK = 7
private const val DAYS_PER_MONTH = 30
private const val DAYS_PER_YEAR = 365

private enum class StorageSheet { NONE, TRASH, PHOTOS, HISTORY }

/**
 * What the app throws away by itself, and when. Out of the box the answer
 * is nothing: the trash waits to be emptied by hand, and the logs are kept.
 * Each of the three is the user's to change, and the logs can be read from
 * the last row.
 */
@Composable
private fun StorageSettingsRows(onOpenHistory: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(StorageSheet.NONE) }
    val close = { sheet = StorageSheet.NONE }

    FieldRow(
        stringResource(R.string.storage_trash),
        daysLabel(settings.emptyTrashAfterDays, R.string.storage_trash_never),
        { sheet = StorageSheet.TRASH },
    )
    FieldRow(
        stringResource(R.string.storage_photos),
        daysLabel(settings.keepRemovedPhotosDays, R.string.storage_photos_at_once),
        { sheet = StorageSheet.PHOTOS },
    )
    FieldRow(
        stringResource(R.string.storage_history),
        daysLabel(settings.keepHistoryDays, R.string.storage_history_always),
        { sheet = StorageSheet.HISTORY },
    )
    HorizontalDivider()
    FieldRow(stringResource(R.string.storage_see_history_detail), stringResource(R.string.history_title), onOpenHistory, valueFirst = true)

    when (sheet) {
        StorageSheet.NONE -> Unit

        StorageSheet.TRASH -> AnujBottomSheet(onDismiss = close, title = stringResource(R.string.storage_trash)) {
            ChoiceChips(
                /** "Never" first; a wait saved by another version of the app still shows as the selected chip. */
                options = (listOf<Int?>(null) + AppSettings.TRASH_DAYS_CHOICES + settings.emptyTrashAfterDays).distinct(),
                selected = settings.emptyTrashAfterDays,
                label = { daysLabel(it, R.string.storage_trash_never) },
                onSelect = viewModel::setEmptyTrashAfter,
            )
            SheetNote(R.string.storage_trash_note)
        }

        StorageSheet.PHOTOS -> AnujBottomSheet(onDismiss = close, title = stringResource(R.string.storage_photos)) {
            ChoiceChips(
                options = (AppSettings.PHOTO_DAYS_CHOICES + settings.keepRemovedPhotosDays).distinct().sorted(),
                selected = settings.keepRemovedPhotosDays,
                label = { daysLabel(it, R.string.storage_photos_at_once) },
                onSelect = viewModel::setKeepRemovedPhotos,
            )
            SheetNote(R.string.storage_photos_note)
        }

        StorageSheet.HISTORY -> AnujBottomSheet(onDismiss = close, title = stringResource(R.string.storage_history)) {
            ChoiceChips(
                options = (listOf<Int?>(null) + AppSettings.HISTORY_DAYS_CHOICES + settings.keepHistoryDays).distinct(),
                selected = settings.keepHistoryDays,
                label = { daysLabel(it, R.string.storage_history_always) },
                onSelect = viewModel::setKeepHistory,
            )
            SheetNote(R.string.storage_history_note)
        }
    }
}

@Composable
private fun SheetNote(@StringRes text: Int) {
    Spacer(Modifier.height(16.dp))
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
