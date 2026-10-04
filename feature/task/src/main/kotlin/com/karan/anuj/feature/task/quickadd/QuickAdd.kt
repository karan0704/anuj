package com.karan.anuj.feature.task.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
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
import com.karan.anuj.core.domain.task.SuggestTaskNamesUseCase
import com.karan.anuj.core.domain.task.TaskDraft
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.feature.task.R
import com.karan.anuj.feature.task.common.DateChips
import com.karan.anuj.feature.task.common.Day
import com.karan.anuj.feature.task.common.WriteScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(
    parentId: TaskId?,
    defaultDate: LocalDate?,
    onDismiss: () -> Unit,
    viewModel: QuickAddViewModel = hiltViewModel(),
) {
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val today = remember { Day.now().date }
    var name by rememberSaveable { mutableStateOf("") }
    /** Stored as a day number because a LocalDate cannot be saved across rotation as it is. */
    var dateDay by rememberSaveable { mutableStateOf(defaultDate?.toEpochDay()) }
    val date = dateDay?.let(LocalDate::ofEpochDay)
    val focus = remember { FocusRequester() }

    val add = { taskName: String ->
        if (taskName.isNotBlank()) {
            viewModel.add(taskName, parentId, date)
            onDismiss()
        }
    }

    LaunchedEffect(Unit) { viewModel.loadSuggestions() }

    AnujBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(if (parentId == null) R.string.quick_add_title else R.string.quick_add_subtask_title),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text(stringResource(R.string.quick_add_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add(name) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
        Spacer(Modifier.height(12.dp))
        DateChips(date = date, today = today, onChange = { dateDay = it?.toEpochDay() })

        if (suggestions.isNotEmpty()) {
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
            text = stringResource(R.string.quick_add_button),
            onClick = { add(name) },
            enabled = name.isNotBlank(),
        )

        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
