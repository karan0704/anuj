package com.karan.anuj.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karan.anuj.R
import com.karan.anuj.core.domain.settings.TextScale
import com.karan.anuj.core.domain.settings.ThemeMode
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.MinTouchTarget

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
private fun TextScale.labelRes(): Int = when (this) {
    TextScale.SMALL -> R.string.text_small
    TextScale.NORMAL -> R.string.text_normal
    TextScale.LARGE -> R.string.text_large
    TextScale.EXTRA_LARGE -> R.string.text_extra_large
}

/** Which choice sheet is open. Saved across rotation so the sheet does not vanish mid-choice. */
private enum class OpenSheet { NONE, THEME, TEXT_SIZE }

/**
 * Every setting is changed by tapping: a row opens a sheet of chips, or
 * carries its own switch. Nothing on this screen is typed.
 *
 * @param lockAvailable false when the phone has no screen lock to check against
 * @param onAppLockToggled handled by the activity, because switching the lock
 * on first shows the system unlock prompt
 */
@Composable
fun SettingsScreen(
    lockAvailable: Boolean,
    onAppLockToggled: (Boolean) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var openSheet by rememberSaveable { mutableStateOf(OpenSheet.NONE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_theme)) },
            supportingContent = { Text(stringResource(settings.themeMode.labelRes())) },
            modifier = Modifier
                .heightIn(min = MinTouchTarget)
                .clickable { openSheet = OpenSheet.THEME },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_text_size)) },
            supportingContent = { Text(stringResource(settings.textScale.labelRes())) },
            modifier = Modifier
                .heightIn(min = MinTouchTarget)
                .clickable { openSheet = OpenSheet.TEXT_SIZE },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_app_lock)) },
            supportingContent = {
                Text(
                    stringResource(
                        when {
                            !lockAvailable -> R.string.settings_app_lock_unavailable
                            settings.appLockEnabled -> R.string.settings_app_lock_on
                            else -> R.string.settings_app_lock_off
                        },
                    ),
                )
            },
            trailingContent = {
                Switch(
                    checked = settings.appLockEnabled,
                    onCheckedChange = onAppLockToggled,
                    /** Still switchable off when the screen lock has gone, so the setting cannot get stuck on. */
                    enabled = lockAvailable || settings.appLockEnabled,
                )
            },
            modifier = Modifier.heightIn(min = MinTouchTarget),
        )
    }

    when (openSheet) {
        OpenSheet.NONE -> Unit

        OpenSheet.THEME -> AnujBottomSheet(
            onDismiss = { openSheet = OpenSheet.NONE },
            title = stringResource(R.string.settings_theme),
        ) {
            ChoiceChips(
                options = ThemeMode.entries,
                selected = settings.themeMode,
                label = { stringResource(it.labelRes()) },
                onSelect = viewModel::setThemeMode,
            )
        }

        OpenSheet.TEXT_SIZE -> AnujBottomSheet(
            onDismiss = { openSheet = OpenSheet.NONE },
            title = stringResource(R.string.settings_text_size),
        ) {
            ChoiceChips(
                options = TextScale.entries,
                selected = settings.textScale,
                label = { stringResource(it.labelRes()) },
                onSelect = viewModel::setTextScale,
            )
            Spacer(Modifier.height(16.dp))
            /** Drawn inside the themed app, so it shows the chosen size as soon as a chip is tapped. */
            Text(
                stringResource(R.string.settings_text_size_preview),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
