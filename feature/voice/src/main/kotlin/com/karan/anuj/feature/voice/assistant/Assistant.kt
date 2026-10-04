package com.karan.anuj.feature.voice.assistant

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.voice.Speech
import com.karan.anuj.core.domain.voice.SpeechFailure
import com.karan.anuj.core.domain.voice.TalkUseCase
import com.karan.anuj.core.domain.voice.VoiceReply
import com.karan.anuj.core.domain.voice.VoiceSettings
import com.karan.anuj.core.domain.voice.VoiceSettingsUseCase
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.SecondaryButton
import com.karan.anuj.core.ui.components.SectionTitle
import com.karan.anuj.feature.voice.R
import com.karan.anuj.feature.voice.platform.VoiceRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * @property heard what the microphone has made out so far
 * @property busy an answer is being worked out or read aloud
 * @property nothingHeard the wait ran out without a word
 */
data class AssistantState(
    val name: String = VoiceSettings.DEFAULT_WAKE_NAME,
    val listening: Boolean = false,
    val busy: Boolean = false,
    val heard: String = "",
    val reply: VoiceReply? = null,
    val failure: SpeechFailure? = null,
    val nothingHeard: Boolean = false,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val talk: TalkUseCase,
    private val settings: VoiceSettingsUseCase,
    private val runner: VoiceRunner,
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantState())
    val state: StateFlow<AssistantState> = _state.asStateFlow()
    private var listening: Job? = null

    val canUseMicrophone: Boolean get() = runner.canUseMicrophone

    init {
        viewModelScope.launch { settings.observe().collect { current -> _state.update { it.copy(name = current.wakeName) } } }
    }

    /** Opens the microphone for one sentence, or closes it if it is open. */
    fun toggleListening() {
        if (listening?.isActive == true) {
            stopListening()
            return
        }
        talk.stopSpeaking()
        _state.update { it.copy(listening = true, heard = "", failure = null, nothingHeard = false) }
        listening = viewModelScope.launch {
            val wait = settings.observe().first().listenSeconds * MILLIS_PER_SECOND
            val ended = withTimeoutOrNull(wait) {
                talk.listen()
                    .onEach { speech -> if (speech is Speech.Partial) _state.update { it.copy(heard = speech.text) } }
                    .first { it !is Speech.Partial }
            }
            /** A sentence the engine had not finished when the wait ran out is still what the user said. */
            val unfinished = _state.value.heard
            _state.update { it.copy(listening = false) }
            when {
                /** The phone's recogniser ends with an empty sentence when it heard only silence. */
                ended is Speech.Final && ended.text.isBlank() -> _state.update { it.copy(nothingHeard = true) }
                ended is Speech.Final -> ask(ended.text)
                ended is Speech.Failed -> _state.update { it.copy(failure = ended.reason) }
                unfinished.isNotBlank() -> ask(unfinished)
                else -> _state.update { it.copy(nothingHeard = true) }
            }
        }
    }

    /** A spoken sentence and a tapped shortcut both arrive here. */
    fun ask(text: String) {
        _state.update { it.copy(busy = true, heard = text, failure = null, nothingHeard = false) }
        runner.launch {
            val reply = talk.say(text)
            _state.update { it.copy(busy = false, reply = reply) }
        }
    }

    fun undo() {
        _state.update { it.copy(busy = true) }
        runner.launch {
            val reply = talk.undo()
            _state.update { it.copy(busy = false, reply = reply) }
        }
    }

    /** The sheet is closing: the microphone and the voice both stop, but a change already asked for still finishes. */
    fun close() {
        stopListening()
        talk.stopSpeaking()
    }

    private fun stopListening() {
        listening?.cancel()
        _state.update { it.copy(listening = false) }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/**
 * The assistant, by tap. One big button opens the microphone for a
 * sentence; three shortcuts ask the commonest questions without speaking.
 * The answer is shown here and read aloud, and anything it changed can be
 * undone with one tap.
 *
 * @param onOpenTask opens the task an answer is about
 */
@Composable
fun AssistantSheet(
    onDismiss: () -> Unit,
    onOpenTask: (TaskId) -> Unit,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val askForMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.toggleListening()
    }
    val talk = {
        if (viewModel.canUseMicrophone) viewModel.toggleListening() else askForMicrophone.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(Unit) { onDispose { viewModel.close() } }

    AnujBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.assistant_title, state.name)) {
        Answer(state)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(if (state.listening) R.string.assistant_stop else R.string.assistant_tap_to_talk),
            onClick = talk,
        )
        val reply = state.reply
        if (reply?.undo != null && !state.busy) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = stringResource(R.string.voice_undo), onClick = viewModel::undo)
        }
        val about = reply?.openTask
        if (about != null && !state.busy) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = stringResource(R.string.assistant_open_task),
                onClick = {
                    onDismiss()
                    onOpenTask(about)
                },
            )
        }
        if (state.failure == SpeechFailure.NO_PERMISSION) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = stringResource(R.string.assistant_allow),
                onClick = { askForMicrophone.launch(Manifest.permission.RECORD_AUDIO) },
            )
        }

        SectionTitle(stringResource(R.string.assistant_try))
        listOf(R.string.assistant_ask_now, R.string.assistant_ask_list, R.string.assistant_ask_priority).forEach { question ->
            val text = stringResource(question)
            TextButton(onClick = { viewModel.ask(text) }, modifier = Modifier.fillMaxWidth()) { Text(text) }
        }
    }
}

/** What was heard and what the assistant answered. Announced by a screen reader as it changes. */
@Composable
private fun Answer(state: AssistantState) {
    val reply = state.reply
    Column(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        when {
            state.listening -> Text(
                text = state.heard.ifBlank { stringResource(R.string.assistant_listening) },
                style = MaterialTheme.typography.titleLarge,
            )
            state.busy -> {
                if (state.heard.isNotBlank()) Heard(state.heard)
                Text(stringResource(R.string.assistant_thinking), style = MaterialTheme.typography.titleLarge)
            }
            state.failure != null -> Text(stringResource(state.failure.message()), style = MaterialTheme.typography.bodyLarge)
            state.nothingHeard -> Text(stringResource(R.string.assistant_nothing_heard), style = MaterialTheme.typography.bodyLarge)
            reply != null -> {
                if (state.heard.isNotBlank()) Heard(state.heard)
                Text(reply.spoken, style = MaterialTheme.typography.titleLarge)
                reply.lines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Heard(text: String) {
    Text(
        text = stringResource(R.string.assistant_heard, text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun SpeechFailure.message(): Int = when (this) {
    SpeechFailure.NO_PERMISSION -> R.string.assistant_no_permission
    SpeechFailure.NO_MODEL -> R.string.assistant_no_model
    SpeechFailure.MICROPHONE_BUSY -> R.string.assistant_mic_busy
}
