package com.karan.anuj.feature.voice.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.voice.ListenWhen
import com.karan.anuj.core.domain.voice.Speech
import com.karan.anuj.core.domain.voice.TalkUseCase
import com.karan.anuj.core.domain.voice.VoiceSettings
import com.karan.anuj.core.domain.voice.VoiceSettingsUseCase
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.FieldRow
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.ScreenHeader
import com.karan.anuj.core.ui.components.ScreenPadding
import com.karan.anuj.core.ui.components.SecondaryButton
import com.karan.anuj.core.ui.components.SectionTitle
import com.karan.anuj.core.ui.components.SwitchRow
import com.karan.anuj.feature.voice.R
import com.karan.anuj.feature.voice.platform.VoiceRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val settings: VoiceSettingsUseCase,
    private val talk: TalkUseCase,
    private val runner: VoiceRunner,
) : ViewModel() {

    val state: StateFlow<VoiceSettings> =
        settings.observe().stateIn(viewModelScope, SharingStarted.Eagerly, VoiceSettings())

    private val _hearing = MutableStateFlow(false)
    val hearing: StateFlow<Boolean> = _hearing.asStateFlow()
    private var job: Job? = null

    val canUseMicrophone: Boolean get() = runner.canUseMicrophone

    fun setName(name: String) = runner.launch { settings.setWakeName(name) }
    fun setListenWhen(listenWhen: ListenWhen) = runner.launch { settings.setListenWhen(listenWhen) }
    fun setSpeakReplies(on: Boolean) = runner.launch { settings.setSpeakReplies(on) }
    fun setMaxSpokenItems(count: Int) = runner.launch { settings.setMaxSpokenItems(count) }
    fun setListenSeconds(seconds: Int) = runner.launch { settings.setListenSeconds(seconds) }
    fun forgetLearntNames() = runner.launch { settings.forgetLearntNames() }

    /** Listens once and keeps whatever the engine wrote as one more way the name sounds. */
    fun hearName() {
        if (job?.isActive == true) return
        _hearing.value = true
        job = viewModelScope.launch {
            val heard = withTimeoutOrNull(TEACH_WAIT_MILLIS) { talk.listen().first { it !is Speech.Partial } }
            _hearing.value = false
            if (heard is Speech.Final) settings.learnName(heard.text)
        }
    }

    fun stopHearing() {
        job?.cancel()
        _hearing.value = false
    }

    private companion object {
        const val TEACH_WAIT_MILLIS = 6_000L
    }
}

private enum class VoiceSheet { NONE, NAME, TEACH, LISTEN_WHEN, ITEMS, WAIT }

@Composable
fun VoiceSettingsScreen(
    onBack: () -> Unit,
    viewModel: VoiceSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(VoiceSheet.NONE) }
    val close = { sheet = VoiceSheet.NONE }
    /** Listening for the name needs the microphone, so choosing it asks for the permission on the spot. */
    var wanted by rememberSaveable { mutableStateOf<ListenWhen?>(null) }
    val askForMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) wanted?.let(viewModel::setListenWhen)
        wanted = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.voice_settings_title), onBack = onBack, backLabel = stringResource(R.string.voice_back))
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            FieldRow(stringResource(R.string.voice_name), settings.wakeName, onClick = { sheet = VoiceSheet.NAME })
            FieldRow(
                label = stringResource(R.string.voice_teach),
                value = if (settings.soundsLike.isEmpty()) {
                    stringResource(R.string.voice_teach_none)
                } else {
                    pluralStringResource(R.plurals.voice_teach_count, settings.soundsLike.size, settings.soundsLike.size)
                },
                onClick = { sheet = VoiceSheet.TEACH },
            )
            FieldRow(stringResource(R.string.voice_listen_when), listenLabel(settings.listenWhen), onClick = { sheet = VoiceSheet.LISTEN_WHEN })
            SwitchRow(stringResource(R.string.voice_speak), settings.speakReplies, viewModel::setSpeakReplies)
            FieldRow(
                label = stringResource(R.string.voice_items),
                value = stringResource(R.string.voice_items_value, settings.maxSpokenItems),
                onClick = { sheet = VoiceSheet.ITEMS },
            )
            FieldRow(
                label = stringResource(R.string.voice_wait),
                value = stringResource(R.string.voice_wait_value, settings.listenSeconds),
                onClick = { sheet = VoiceSheet.WAIT },
            )
            SectionTitle(stringResource(R.string.voice_examples_title))
            stringArrayResource(R.array.voice_examples).forEach { example ->
                Text(
                    text = example,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (sheet) {
        VoiceSheet.NONE -> Unit
        VoiceSheet.NAME -> NameSheet(settings.wakeName, onSave = { viewModel.setName(it); close() }, onDismiss = close)
        VoiceSheet.TEACH -> TeachSheet(settings, viewModel, onDismiss = close)
        VoiceSheet.LISTEN_WHEN -> AnujBottomSheet(onDismiss = close, title = stringResource(R.string.voice_listen_when)) {
            ChoiceChips(
                options = ListenWhen.entries,
                selected = settings.listenWhen,
                label = { listenLabel(it) },
                onSelect = { choice ->
                    if (choice == ListenWhen.NEVER || viewModel.canUseMicrophone) {
                        viewModel.setListenWhen(choice)
                    } else {
                        wanted = choice
                        askForMicrophone.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.voice_listen_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = stringResource(R.string.voice_ok), onClick = close)
        }
        VoiceSheet.ITEMS -> NumberSheet(
            title = stringResource(R.string.voice_items),
            options = VoiceSettings.SPOKEN_ITEM_CHOICES,
            selected = settings.maxSpokenItems,
            label = { stringResource(R.string.voice_items_value, it) },
            onSelect = viewModel::setMaxSpokenItems,
            onDismiss = close,
        )
        VoiceSheet.WAIT -> NumberSheet(
            title = stringResource(R.string.voice_wait),
            options = VoiceSettings.LISTEN_SECONDS_CHOICES,
            selected = settings.listenSeconds,
            label = { stringResource(R.string.voice_wait_value, it) },
            onSelect = viewModel::setListenSeconds,
            onDismiss = close,
        )
    }
}

@Composable
private fun listenLabel(listenWhen: ListenWhen): String = stringResource(
    when (listenWhen) {
        ListenWhen.NEVER -> R.string.voice_listen_never
        ListenWhen.WHILE_CHARGING -> R.string.voice_listen_charging
        ListenWhen.WHILE_SCREEN_ON -> R.string.voice_listen_screen_on
        ListenWhen.ALWAYS -> R.string.voice_listen_always
    },
)

@Composable
private fun NumberSheet(
    title: String,
    options: List<Int>,
    selected: Int,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AnujBottomSheet(onDismiss = onDismiss, title = title) {
        ChoiceChips(options = options, selected = selected, label = label, onSelect = onSelect)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.voice_ok), onClick = onDismiss)
    }
}

/** The name is the one thing here that is written, because it can be any word the user likes. */
@Composable
private fun NameSheet(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AnujBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.voice_name)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text(stringResource(R.string.voice_name_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onSave(name) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.voice_ok), onClick = { onSave(name) }, enabled = name.isNotBlank())
    }
}

/**
 * The offline engine only writes dictionary words, so a name like "Anuj"
 * comes out as something else. Saying it a few times here shows the engine's
 * versions, and each one is kept as a way to wake the assistant.
 */
@Composable
private fun TeachSheet(settings: VoiceSettings, viewModel: VoiceSettingsViewModel, onDismiss: () -> Unit) {
    val hearing by viewModel.hearing.collectAsStateWithLifecycle()
    val askForMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.hearName()
    }
    DisposableEffect(Unit) { onDispose { viewModel.stopHearing() } }

    AnujBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.voice_teach)) {
        Text(stringResource(R.string.voice_teach_body, settings.wakeName), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(if (hearing) R.string.assistant_listening else R.string.voice_teach_say),
            onClick = { if (viewModel.canUseMicrophone) viewModel.hearName() else askForMicrophone.launch(Manifest.permission.RECORD_AUDIO) },
            enabled = !hearing,
        )
        if (settings.soundsLike.isNotEmpty()) {
            SectionTitle(stringResource(R.string.voice_teach_heard))
            settings.soundsLike.sorted().forEach { phrase ->
                Text(phrase, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
            }
            TextButton(onClick = { viewModel.forgetLearntNames() }) { Text(stringResource(R.string.voice_teach_forget)) }
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton(text = stringResource(R.string.voice_teach_done), onClick = onDismiss)
    }
}
