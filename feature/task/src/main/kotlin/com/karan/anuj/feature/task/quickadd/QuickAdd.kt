package com.karan.anuj.feature.task.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.task.CreateTaskUseCase
import com.karan.anuj.core.domain.task.Repetition
import com.karan.anuj.core.domain.task.SuggestTaskNamesUseCase
import com.karan.anuj.core.domain.task.TaskDraft
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.ChoiceRow
import com.karan.anuj.core.ui.components.LocalVoiceInput
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.feature.task.R
import com.karan.anuj.feature.task.common.DateChips
import com.karan.anuj.feature.task.common.Day
import com.karan.anuj.feature.task.common.Weekend
import com.karan.anuj.feature.task.common.WriteScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the add button can make. A reminder is made on its own screen, so it is not listed here. */
enum class AddKind { TASK, ROUTINE }

/** How often a new routine comes round; anything finer is set on the routine itself. */
private enum class Rhythm(val label: Int, val repetition: Repetition, val daysOff: Set<DayOfWeek> = emptySet()) {
    DAILY(R.string.repeat_daily, Repetition.Daily()),
    WEEKDAYS(R.string.repeat_weekdays, Repetition.Daily(), Weekend),
    WEEKLY(R.string.repeat_weekly, Repetition.Weekly()),
}

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val createTask: CreateTaskUseCase,
    private val suggestNames: SuggestTaskNamesUseCase,
    private val writes: WriteScope,
) : ViewModel() {

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    /** Read again each time the sheet opens, so a task just added is offered straight away. */
    fun loadSuggestions() {
        viewModelScope.launch { _suggestions.value = suggestNames() }
    }

    fun add(name: String, parentId: TaskId?, date: LocalDate?) {
        writes.launch {
            createTask(TaskDraft(name = name, parentId = parentId, dueDate = date), today = Day.now().date)
        }
    }

    /** A routine starts today; its first day can be changed on the routine itself. */
    fun addRoutine(name: String, repetition: Repetition, daysOff: Set<DayOfWeek>) {
        writes.launch {
            createTask(TaskDraft(name = name, repetition = repetition, daysOff = daysOff), today = Day.now().date)
        }
    }
}

/**
 * What the add button opens. It asks one thing first, in plain words: is
 * this something to do once, something that comes round again, or only a
 * nudge at a certain time. The three look alike in a list and behave
 * differently, so the choice is made here, where it is easiest to explain.
 *
 * @param onAddReminder opens the screen where a reminder is made; when null the choice is not offered
 */
@Composable
fun AddSheet(
    defaultDate: LocalDate?,
    onDismiss: () -> Unit,
    onAddReminder: (() -> Unit)? = null,
) {
    var kind by rememberSaveable { mutableStateOf<AddKind?>(null) }
    val chosen = kind
    if (chosen != null) {
        QuickAddSheet(parentId = null, defaultDate = defaultDate, onDismiss = onDismiss, kind = chosen)
        return
    }
    AnujBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.add_kind_title)) {
        ChoiceRow(
            title = stringResource(R.string.kind_task),
            detail = stringResource(R.string.add_kind_task_detail),
            leading = { Icon(Icons.Filled.Check, contentDescription = null) },
            onClick = { kind = AddKind.TASK },
        )
        ChoiceRow(
            title = stringResource(R.string.kind_routine),
            detail = stringResource(R.string.add_kind_routine_detail),
            leading = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            onClick = { kind = AddKind.ROUTINE },
        )
        if (onAddReminder != null) {
            ChoiceRow(
                title = stringResource(R.string.kind_reminder),
                detail = stringResource(R.string.add_kind_reminder_detail),
                leading = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onAddReminder()
                },
            )
        }
    }
}

/**
 * The fastest way to get a task in: a name, a day, done. Everything else can
 * be set later from the task itself.
 *
 * Names used before are offered as chips; tapping one adds that task at
 * once, so something done regularly takes two taps and no typing.
 *
 * @param parentId set to add a step under an existing task
 * @param defaultDate the day chip that starts selected; null for "No date"
 * @param kind a routine is asked how often it comes round instead of which day
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(
    parentId: TaskId?,
    defaultDate: LocalDate?,
    onDismiss: () -> Unit,
    kind: AddKind = AddKind.TASK,
    viewModel: QuickAddViewModel = hiltViewModel(),
) {
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val today = remember { Day.now().date }
    var name by rememberSaveable { mutableStateOf("") }
    /** Stored as a day number because a LocalDate cannot be saved across rotation as it is. */
    var dateDay by rememberSaveable { mutableStateOf(defaultDate?.toEpochDay()) }
    val date = dateDay?.let(LocalDate::ofEpochDay)
    var rhythm by rememberSaveable { mutableStateOf(Rhythm.DAILY) }
    val focus = remember { FocusRequester() }

    val add = { taskName: String ->
        if (taskName.isNotBlank()) {
            when (kind) {
                AddKind.TASK -> viewModel.add(taskName, parentId, date)
                AddKind.ROUTINE -> viewModel.addRoutine(taskName, rhythm.repetition, rhythm.daysOff)
            }
            onDismiss()
        }
    }

    LaunchedEffect(Unit) { viewModel.loadSuggestions() }

    AnujBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(
            when {
                parentId != null -> R.string.quick_add_subtask_title
                kind == AddKind.ROUTINE -> R.string.quick_add_routine_title
                else -> R.string.quick_add_title
            },
        ),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text(stringResource(R.string.quick_add_name)) },
            /** Say the name instead of typing it. */
            trailingIcon = { LocalVoiceInput.current { spoken -> name = spoken } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add(name) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
        Spacer(Modifier.height(12.dp))
        when (kind) {
            AddKind.TASK -> DateChips(date = date, today = today, onChange = { dateDay = it?.toEpochDay() })
            AddKind.ROUTINE -> ChoiceChips(
                options = Rhythm.entries,
                selected = rhythm,
                label = { stringResource(it.label) },
                onSelect = { rhythm = it },
            )
        }

        /** A name used before is a one-off task being added again, so it is not offered for a routine. */
        if (suggestions.isNotEmpty() && kind == AddKind.TASK) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.quick_add_suggestions), style = MaterialTheme.typography.titleSmall)
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { suggestion ->
                    AssistChip(onClick = { add(suggestion) }, label = { Text(suggestion) })
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(if (kind == AddKind.ROUTINE) R.string.quick_add_routine_button else R.string.quick_add_button),
            onClick = { add(name) },
            enabled = name.isNotBlank(),
        )

        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
