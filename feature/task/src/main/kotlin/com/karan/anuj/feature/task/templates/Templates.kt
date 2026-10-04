package com.karan.anuj.feature.task.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.karan.anuj.core.domain.task.ApplyTemplatesUseCase
import com.karan.anuj.core.domain.task.BuiltInTemplates
import com.karan.anuj.core.domain.task.TagActions
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.SecondaryButton
import com.karan.anuj.core.ui.components.ToggleChips
import com.karan.anuj.feature.task.R
import com.karan.anuj.feature.task.common.Day
import com.karan.anuj.feature.task.common.WriteScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val applyTemplates: ApplyTemplatesUseCase,
    private val tagActions: TagActions,
    private val writes: WriteScope,
) : ViewModel() {

    /** @param onDone called once the tasks are saved, so the next screen already shows them */
    fun add(keys: Set<String>, onDone: () -> Unit) {
        writes.launch {
            applyTemplates(BuiltInTemplates.all.filter { it.key in keys }, today = Day.now().date)
            onDone()
        }
    }

    /** Gives a new install a few tags to tap, whether or not any routine was chosen. */
    fun ensureStarterTags() {
        writes.launch { tagActions.ensureStarterTags(BuiltInTemplates.starterTags) }
    }
}

/** The routines as chips; any number can be switched on. Keys are saved as text so the choice survives rotation. */
/** Drawn with the shared [ToggleChips], which leaves a gap between wrapped rows. */
@Composable
private fun TemplateChips(selected: Set<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    val names = BuiltInTemplates.all.associate { it.key to it.name }
    ToggleChips(
        options = BuiltInTemplates.all.map { it.key },
        selected = selected,
        label = { names.getValue(it) },
        onToggle = onToggle,
        modifier = modifier,
        centered = true,
    )
}

@Composable
private fun rememberSelection(initial: Set<String>): Pair<Set<String>, (String) -> Unit> {
    var keys by rememberSaveable { mutableStateOf(initial.toList()) }
    val toggle: (String) -> Unit = { key -> keys = if (key in keys) keys - key else keys + key }
    return keys.toSet() to toggle
}

/**
 * The first-run page that offers the ready-made routines, so the app does
 * not open empty. Everything starts selected: one tap accepts them all.
 */
@Composable
fun TemplateOnboardingStep(
    onDone: () -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val (selected, toggle) = rememberSelection(BuiltInTemplates.all.map { it.key }.toSet())
    val finish = {
        viewModel.ensureStarterTags()
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.templates_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.templates_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            TemplateChips(selected, toggle)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(R.string.templates_add),
            onClick = { viewModel.add(selected, finish) },
            enabled = selected.isNotEmpty(),
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton(text = stringResource(R.string.templates_skip), onClick = finish)
    }
}

/** The same routines, offered later from the Tasks screen. Nothing starts selected here. */
@Composable
fun TemplateSheet(
    onDismiss: () -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val (selected, toggle) = rememberSelection(emptySet())

    AnujBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.templates_sheet_title)) {
        TemplateChips(selected, toggle)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(R.string.templates_add),
            onClick = { viewModel.add(selected, onDismiss) },
            enabled = selected.isNotEmpty(),
        )
    }
}
