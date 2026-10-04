package com.karan.anuj.feature.voice.dictate

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.core.domain.voice.Speech
import com.karan.anuj.core.domain.voice.TalkUseCase
import com.karan.anuj.core.domain.voice.VoiceSettingsUseCase
import com.karan.anuj.feature.voice.R
import com.karan.anuj.feature.voice.platform.VoiceRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class DictationViewModel @Inject constructor(
    private val talk: TalkUseCase,
    private val settings: VoiceSettingsUseCase,
    private val runner: VoiceRunner,
) : ViewModel() {

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()
    private var job: Job? = null

    val canUseMicrophone: Boolean get() = runner.canUseMicrophone

    /** Listens for one sentence and hands it to [onText]; a second tap stops without changing the field. */
    fun toggle(onText: (String) -> Unit) {
        if (job?.isActive == true) {
            stop()
            return
        }
        _listening.value = true
        job = viewModelScope.launch {
            val wait = settings.observe().first().listenSeconds * MILLIS_PER_SECOND
            val heard = withTimeoutOrNull(wait) { talk.listen().first { it !is Speech.Partial } }
            _listening.value = false
            if (heard is Speech.Final && heard.text.isNotBlank()) onText(heard.text.replaceFirstChar { it.uppercase() })
        }
    }

    fun stop() {
        job?.cancel()
        _listening.value = false
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/**
 * The microphone on a text field: tap, say it, and the words fill the field.
 * It is what makes typing optional everywhere a name or a note is asked for.
 *
 * @param onText receives what was said, starting with a capital letter
 */
@Composable
fun DictationButton(
    onText: (String) -> Unit,
    viewModel: DictationViewModel = hiltViewModel(),
) {
    val listening by viewModel.listening.collectAsStateWithLifecycle()
    val deliver by rememberUpdatedState(onText)
    val askForMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.toggle { deliver(it) }
    }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    IconButton(
        onClick = {
            if (viewModel.canUseMicrophone) viewModel.toggle { deliver(it) } else askForMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = stringResource(if (listening) R.string.dictate_stop else R.string.dictate_start),
            tint = if (listening) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}
