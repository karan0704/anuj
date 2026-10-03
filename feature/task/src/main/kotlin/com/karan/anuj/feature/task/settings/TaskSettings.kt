package com.karan.anuj.feature.task.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.karan.anuj.core.domain.task.CarryOverRule
import com.karan.anuj.core.domain.task.TaskPreferences
import com.karan.anuj.core.domain.task.TaskPreferencesUseCase
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.FieldRow
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.Stepper
import com.karan.anuj.feature.task.R
import com.karan.anuj.feature.task.common.CarryOverSheet
import com.karan.anuj.feature.task.common.Day
import com.karan.anuj.feature.task.common.WriteScope
import com.karan.anuj.feature.task.common.carryOverLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TaskSettingsViewModel @Inject constructor(
    private val preferences: TaskPreferencesUseCase,
    private val writes: WriteScope,
) : ViewModel() {

    val state: StateFlow<TaskPreferences> =
        preferences.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskPreferences())

    fun setDefaultCarryOver(rule: CarryOverRule) {
        writes.launch { preferences.setDefaultCarryOver(rule) }
    }

    fun setCarryLimit(limit: Int) {
        writes.launch { preferences.setCarryLimit(limit) }
    }
}

private enum class TaskSettingSheet { NONE, CARRY_OVER, CARRY_LIMIT }

/**
 * The task rows of the Settings screen. The settings screen itself belongs
 * to the app; each feature contributes its own rows this way, so adding a
 * feature never means editing another feature's settings.
 */
@Composable
fun TaskSettingsRows(viewModel: TaskSettingsViewModel = hiltViewModel()) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    val today = Day.now().date
    var sheet by rememberSaveable { mutableStateOf(TaskSettingSheet.NONE) }

    FieldRow(
        label = stringResource(R.string.carry_default_title),
        value = carryOverLabel(prefs.defaultCarryOver, today),
        onClick = { sheet = TaskSettingSheet.CARRY_OVER },
    )
    FieldRow(
        label = stringResource(R.string.carry_limit_title),
        value = stringResource(R.string.carry_limit_value, prefs.carryLimit),
        onClick = { sheet = TaskSettingSheet.CARRY_LIMIT },
    )

    when (sheet) {
        TaskSettingSheet.NONE -> Unit
        TaskSettingSheet.CARRY_OVER -> CarryOverSheet(
            title = stringResource(R.string.carry_default_title),
            rule = prefs.defaultCarryOver,
            /** The app-wide default has nothing above it to fall back on, so it must always be a rule. */
            inheritLabel = null,
            today = today,
            onChange = { it?.let(viewModel::setDefaultCarryOver) },
            onDismiss = { sheet = TaskSettingSheet.NONE },
        )
        TaskSettingSheet.CARRY_LIMIT -> AnujBottomSheet(
            onDismiss = { sheet = TaskSettingSheet.NONE },
            title = stringResource(R.string.carry_limit_title),
        ) {
            Stepper(
                value = prefs.carryLimit,
                onChange = viewModel::setCarryLimit,
                range = 1..20,
                lessLabel = stringResource(R.string.repeat_gap_less),
                moreLabel = stringResource(R.string.repeat_gap_more),
                valueText = stringResource(R.string.carry_limit_value, prefs.carryLimit),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = stringResource(R.string.task_ok), onClick = { sheet = TaskSettingSheet.NONE })
        }
    }
}
