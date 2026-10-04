package com.karan.anuj.feature.place.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.place.Place
import com.karan.anuj.core.domain.place.PlaceActions
import com.karan.anuj.core.domain.place.PlaceId
import com.karan.anuj.core.domain.place.PlaceKind
import com.karan.anuj.core.domain.place.PlaceSettings
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.FieldRow
import com.karan.anuj.core.ui.components.LocalVoiceInput
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.ScreenHeader
import com.karan.anuj.core.ui.components.ScreenPadding
import com.karan.anuj.core.ui.components.SecondaryButton
import com.karan.anuj.core.ui.components.SectionTitle
import com.karan.anuj.core.ui.components.SwitchRow
import com.karan.anuj.feature.place.R
import com.karan.anuj.feature.place.platform.PlaceRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What the last "where am I" or "save here" came back with. */
enum class PlaceMessage(@StringRes val text: Int) {
    CHECKING(R.string.places_checking),
    CHECKED(R.string.places_checked),
    NO_POSITION(R.string.places_no_position),
    SAVED(R.string.places_saved_here),
}

/**
 * @property allowed location has been allowed at all
 * @property allowedClosed it has also been allowed while the app is closed
 */
data class PlacesUiState(
    val settings: PlaceSettings = PlaceSettings(),
    val places: List<Place> = emptyList(),
    val allowed: Boolean = false,
    val allowedClosed: Boolean = false,
    val message: PlaceMessage? = null,
)

@HiltViewModel
class PlacesViewModel @Inject constructor(
    private val actions: PlaceActions,
    private val runner: PlaceRunner,
) : ViewModel() {

    private data class Phone(val allowed: Boolean, val allowedClosed: Boolean, val message: PlaceMessage? = null)

    private val phone = MutableStateFlow(Phone(runner.permitted, runner.permittedInBackground))

    val state: StateFlow<PlacesUiState> =
        combine(actions.observeSettings(), actions.observe(), phone) { settings, places, now ->
            PlacesUiState(settings, places, now.allowed, now.allowedClosed, now.message)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlacesUiState())

    /** Permissions are changed outside the app, so they are read again each time the screen comes back. */
    fun refreshPermissions() {
        phone.update { it.copy(allowed = runner.permitted, allowedClosed = runner.permittedInBackground) }
        runner.launch { runner.refreshSchedule() }
    }

    fun update(change: (PlaceSettings) -> PlaceSettings) {
        runner.launch {
            actions.updateSettings(change)
            runner.refreshSchedule()
        }
    }

    fun checkNow() {
        phone.update { it.copy(message = PlaceMessage.CHECKING) }
        runner.launch {
            val found = runner.check()
            phone.update { it.copy(message = if (found) PlaceMessage.CHECKED else PlaceMessage.NO_POSITION) }
        }
    }

    fun saveHere(name: String, isHome: Boolean) {
        phone.update { it.copy(message = PlaceMessage.CHECKING) }
        runner.launch {
            val saved = actions.saveHere(name, isHome)
            phone.update { it.copy(message = if (saved != null) PlaceMessage.SAVED else PlaceMessage.NO_POSITION) }
        }
    }

    fun edit(id: PlaceId, change: (Place) -> Place) {
        runner.launch { actions.edit(id, change) }
    }

    fun remove(id: PlaceId) {
        runner.launch { actions.remove(id) }
    }
}

/** Which sheet is open. A place's id is kept as text so the choice survives rotation. */
private enum class PlacesSheet { NONE, SAVE_HERE, EDIT, RADIUS, NOTICE_MINUTES, NOTICE_VISITS, SETTLE }

@StringRes
private fun PlaceKind.label(): Int = when (this) {
    PlaceKind.SAVED -> R.string.place_kind_saved
    PlaceKind.VISITED -> R.string.place_kind_visited
    PlaceKind.IGNORED -> R.string.place_kind_ignored
    PlaceKind.NOTICED, PlaceKind.SEEN -> R.string.place_kind_noticed
}

/**
 * Places: the switch, what the phone has been allowed, the places
 * themselves and the numbers the rules use.
 *
 * Nothing is read until the switch is on. A spot the phone has noticed
 * waits at the top with its three answers: save it, keep it only as
 * visited, or ignore it.
 */
@Composable
fun PlacesScreen(onBack: () -> Unit, viewModel: PlacesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(PlacesSheet.NONE) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val close = {
        sheet = PlacesSheet.NONE
        editingId = null
    }
    val settings = state.settings

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose {}
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        viewModel.refreshPermissions()
        if (granted.values.any { it }) viewModel.update { it.copy(enabled = true) }
    }
    val askClosed = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshPermissions() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.places_title), onBack = onBack, backLabel = stringResource(R.string.places_back))
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SwitchRow(
                label = stringResource(R.string.places_enable),
                detail = stringResource(R.string.places_enable_detail),
                checked = settings.enabled && state.allowed,
                onChange = { on ->
                    when {
                        !on -> viewModel.update { it.copy(enabled = false) }
                        state.allowed -> viewModel.update { it.copy(enabled = true) }
                        else -> askLocation.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    }
                },
            )
            if (settings.enabled && state.allowed) {
                if (!state.allowedClosed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    FieldRow(
                        label = stringResource(R.string.places_closed_detail),
                        value = stringResource(R.string.places_closed),
                        onClick = { askClosed.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                        valueFirst = true,
                    )
                }
                FieldRow(
                    label = stringResource(state.message?.text ?: R.string.places_check_detail),
                    value = stringResource(R.string.places_check),
                    onClick = viewModel::checkNow,
                    valueFirst = true,
                )
                TextButton(onClick = { sheet = PlacesSheet.SAVE_HERE }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.places_save_here))
                }
            }

            PlaceGroup(R.string.places_group_noticed, state.places.filter { it.kind == PlaceKind.NOTICED }) { place ->
                NoticedRow(
                    onSave = {
                        editingId = place.id.value
                        sheet = PlacesSheet.EDIT
                    },
                    onVisited = { viewModel.edit(place.id) { it.copy(kind = PlaceKind.VISITED) } },
                    onIgnore = { viewModel.edit(place.id) { it.copy(kind = PlaceKind.IGNORED) } },
                )
            }
            listOf(
                R.string.places_group_saved to PlaceKind.SAVED,
                R.string.places_group_visited to PlaceKind.VISITED,
                R.string.places_group_ignored to PlaceKind.IGNORED,
            ).forEach { (title, kind) ->
                PlaceGroup(title, state.places.filter { it.kind == kind }) { place ->
                    FieldRow(
                        label = stringResource(if (place.isHome) R.string.place_is_home else kind.label()),
                        value = place.name.ifBlank { stringResource(R.string.place_unnamed) },
                        onClick = {
                            editingId = place.id.value
                            sheet = PlacesSheet.EDIT
                        },
                        valueFirst = true,
                    )
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.places_rules))
            FieldRow(stringResource(R.string.places_radius), stringResource(R.string.places_metres, settings.radiusMeters), { sheet = PlacesSheet.RADIUS })
            SwitchRow(
                label = stringResource(R.string.places_ask),
                detail = stringResource(R.string.places_ask_detail),
                checked = settings.askAboutNewPlaces,
                onChange = { on -> viewModel.update { it.copy(askAboutNewPlaces = on) } },
            )
            FieldRow(
                stringResource(R.string.places_notice_minutes),
                stringResource(R.string.places_minutes, settings.noticeAfterMinutes),
                { sheet = PlacesSheet.NOTICE_MINUTES },
            )
            FieldRow(
                stringResource(R.string.places_notice_visits),
                stringResource(R.string.places_visits, settings.noticeAfterVisits),
                { sheet = PlacesSheet.NOTICE_VISITS },
            )
            FieldRow(stringResource(R.string.places_settle), stringResource(R.string.places_minutes, settings.settleMinutes), { sheet = PlacesSheet.SETTLE })
            Spacer(Modifier.height(24.dp))
        }
    }

    when (sheet) {
        PlacesSheet.NONE -> Unit
        PlacesSheet.SAVE_HERE -> PlaceSheet(
            title = stringResource(R.string.places_save_here),
            place = null,
            noHomeYet = state.places.none { it.isHome },
            onSave = { name, home, _ -> viewModel.saveHere(name, home) },
            onRemove = null,
            onDismiss = close,
        )
        PlacesSheet.EDIT -> state.places.firstOrNull { it.id.value == editingId }?.let { place ->
            PlaceSheet(
                title = place.name.ifBlank { stringResource(R.string.place_unnamed) },
                place = place,
                noHomeYet = false,
                onSave = { name, home, kind -> viewModel.edit(place.id) { it.copy(name = name, isHome = home, kind = kind) } },
                onRemove = { viewModel.remove(place.id) },
                onDismiss = close,
            )
        }
        PlacesSheet.RADIUS -> NumberSheet(
            R.string.places_radius, PlaceSettings.RADIUS_CHOICES, settings.radiusMeters, R.string.places_metres, close,
        ) { value -> viewModel.update { it.copy(radiusMeters = value) } }
        PlacesSheet.NOTICE_MINUTES -> NumberSheet(
            R.string.places_notice_minutes, PlaceSettings.NOTICE_MINUTES_CHOICES, settings.noticeAfterMinutes, R.string.places_minutes, close,
        ) { value -> viewModel.update { it.copy(noticeAfterMinutes = value) } }
        PlacesSheet.NOTICE_VISITS -> NumberSheet(
            R.string.places_notice_visits, PlaceSettings.NOTICE_VISITS_CHOICES, settings.noticeAfterVisits, R.string.places_visits, close,
        ) { value -> viewModel.update { it.copy(noticeAfterVisits = value) } }
        PlacesSheet.SETTLE -> NumberSheet(
            R.string.places_settle, PlaceSettings.SETTLE_MINUTES_CHOICES, settings.settleMinutes, R.string.places_minutes, close,
        ) { value -> viewModel.update { it.copy(settleMinutes = value) } }
    }
}

/** A heading and its places; nothing at all is drawn for an empty group. */
@Composable
private fun PlaceGroup(@StringRes title: Int, places: List<Place>, row: @Composable (Place) -> Unit) {
    if (places.isEmpty()) return
    HorizontalDivider()
    SectionTitle(stringResource(title))
    places.forEach { place -> row(place) }
}

/** The three answers to "you keep coming here". */
@Composable
private fun NoticedRow(onSave: () -> Unit, onVisited: () -> Unit, onIgnore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.place_noticed_question), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        PrimaryButton(text = stringResource(R.string.place_answer_save), onClick = onSave)
        Spacer(Modifier.height(8.dp))
        SecondaryButton(text = stringResource(R.string.place_answer_visited), onClick = onVisited)
        Spacer(Modifier.height(8.dp))
        SecondaryButton(text = stringResource(R.string.place_answer_ignore), onClick = onIgnore)
    }
}

/**
 * Naming a place, new or existing. Saving a noticed spot from here turns it
 * into a saved place.
 *
 * @param place null when the place is being made from where the phone is now
 * @param onRemove null hides "Remove"
 */
@Composable
private fun PlaceSheet(
    title: String,
    place: Place?,
    noHomeYet: Boolean,
    onSave: (name: String, isHome: Boolean, kind: PlaceKind) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(place?.name.orEmpty()) }
    var home by rememberSaveable { mutableStateOf(place?.isHome ?: noHomeYet) }
    var kind by rememberSaveable { mutableStateOf(place?.kind?.takeIf { it != PlaceKind.NOTICED } ?: PlaceKind.SAVED) }

    AnujBottomSheet(onDismiss = onDismiss, title = title) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text(stringResource(R.string.place_name_hint)) },
            trailingIcon = { LocalVoiceInput.current { spoken -> name = spoken } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        if (place != null) {
            Spacer(Modifier.height(12.dp))
            ChoiceChips(
                options = listOf(PlaceKind.SAVED, PlaceKind.VISITED, PlaceKind.IGNORED),
                selected = kind,
                label = { stringResource(it.label()) },
                onSelect = { kind = it },
            )
        }
        if (kind == PlaceKind.SAVED) {
            SwitchRow(
                label = stringResource(R.string.place_home),
                detail = stringResource(R.string.place_home_detail),
                checked = home,
                onChange = { home = it },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = stringResource(R.string.place_save),
            onClick = {
                onSave(name, home && kind == PlaceKind.SAVED, kind)
                onDismiss()
            },
        )
        if (onRemove != null) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = stringResource(R.string.place_remove),
                onClick = {
                    onRemove()
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun NumberSheet(
    @StringRes title: Int,
    choices: List<Int>,
    selected: Int,
    @StringRes unit: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AnujBottomSheet(onDismiss = onDismiss, title = stringResource(title)) {
        ChoiceChips(
            /** A number saved by another version of the app still shows as the selected chip. */
            options = (choices + selected).distinct().sorted(),
            selected = selected,
            label = { stringResource(unit, it) },
            onSelect = onSelect,
        )
    }
}
