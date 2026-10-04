package com.karan.anuj.feature.place.task

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karan.anuj.core.domain.place.Place
import com.karan.anuj.core.domain.place.PlaceActions
import com.karan.anuj.core.domain.place.PlaceKind
import com.karan.anuj.core.domain.place.PlaceMoment
import com.karan.anuj.core.domain.place.TaskPlace
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.FieldRow
import com.karan.anuj.feature.place.R
import com.karan.anuj.feature.place.platform.PlaceRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@HiltViewModel
class TaskPlaceViewModel @Inject constructor(
    private val actions: PlaceActions,
    private val runner: PlaceRunner,
) : ViewModel() {

    /** Only a saved place can have a task tied to it. */
    val places: Flow<List<Place>> = actions.observe().map { all -> all.filter { it.kind == PlaceKind.SAVED } }

    fun tieOf(taskId: TaskId): Flow<TaskPlace?> = actions.observeTie(taskId)

    fun tie(taskId: TaskId, tie: TaskPlace?) {
        runner.launch { actions.tie(taskId, tie) }
    }
}

/**
 * The "Place" row on a task. It is not drawn until there is a saved place
 * to choose, so a user who does not use places never sees it.
 *
 * Choosing a place and "when I get there" makes the task come up on
 * arriving. "Before I leave" makes the task a leaving list: going out with
 * a step still open brings it up, and its steps start fresh once back in.
 */
@Composable
fun TaskPlaceField(taskId: TaskId, viewModel: TaskPlaceViewModel = hiltViewModel()) {
    val places by viewModel.places.collectAsStateWithLifecycle(initialValue = emptyList())
    val tieFlow = remember(taskId) { viewModel.tieOf(taskId) }
    val tie by tieFlow.collectAsStateWithLifecycle(initialValue = null)
    var open by rememberSaveable { mutableStateOf(false) }
    if (places.isEmpty()) return

    val current = tie
    val place = places.firstOrNull { it.id == current?.placeId }
    val unnamed = stringResource(R.string.place_unnamed)
    FieldRow(
        label = stringResource(R.string.task_place),
        value = when {
            current == null || place == null -> stringResource(R.string.task_place_none)
            current.moment == PlaceMoment.ARRIVE -> stringResource(R.string.task_place_arrive, place.name.ifBlank { unnamed })
            else -> stringResource(R.string.task_place_leave, place.name.ifBlank { unnamed })
        },
        onClick = { open = true },
    )

    if (open) {
        AnujBottomSheet(onDismiss = { open = false }, title = stringResource(R.string.task_place)) {
            ChoiceChips(
                options = listOf<Place?>(null) + places,
                selected = place,
                label = { it?.name?.ifBlank { unnamed } ?: stringResource(R.string.task_place_none) },
                onSelect = { chosen ->
                    viewModel.tie(taskId, chosen?.let { TaskPlace(taskId, it.id, current?.moment ?: PlaceMoment.ARRIVE) })
                },
            )
            if (current != null && place != null) {
                Spacer(Modifier.height(16.dp))
                ChoiceChips(
                    options = PlaceMoment.entries,
                    selected = current.moment,
                    label = { stringResource(if (it == PlaceMoment.ARRIVE) R.string.task_place_when_arrive else R.string.task_place_when_leave) },
                    onSelect = { moment -> viewModel.tie(taskId, current.copy(moment = moment)) },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(
                        if (current.moment == PlaceMoment.ARRIVE) R.string.task_place_arrive_note else R.string.task_place_leave_note,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
