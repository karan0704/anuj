package com.karan.anuj.feature.voice.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.karan.anuj.core.domain.voice.Speech
import com.karan.anuj.core.domain.voice.SpeechEngine
import com.karan.anuj.core.domain.voice.SpeechFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Takes down a sentence with the phone's own speech recognition, the one
 * the phone's keyboard and assistant use. It is far more accurate than the
 * small model bundled with the app.
 *
 * It listens inside this app: no other screen opens. It works without the
 * internet when the phone has its English speech pack downloaded, and uses
 * the internet otherwise. A phone with no speech recognition at all falls
 * back to the bundled model, so the microphone always does something.
 *
 * One call hears one sentence and then ends; that is how the phone's
 * recogniser works, and it is all the assistant and the text fields need.
 */
@Singleton
class PhoneSpeechEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bundled: VoskSpeechEngine,
) : SpeechEngine {

    override fun listen(): Flow<Speech> =
        if (SpeechRecognizer.isRecognitionAvailable(context)) phone() else bundled.listen()

    /** The recogniser must be made and used on the main thread; the phone enforces it. */
    private fun phone(): Flow<Speech> = callbackFlow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            trySend(Speech.Failed(SpeechFailure.NO_PERMISSION))
            close()
            return@callbackFlow
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                best(partialResults)?.let { trySend(Speech.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                trySend(Speech.Final(best(results).orEmpty()))
                close()
            }

            override fun onError(error: Int) {
                trySend(
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Speech.Failed(SpeechFailure.NO_PERMISSION)
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_AUDIO -> Speech.Failed(SpeechFailure.MICROPHONE_BUSY)
                        /** Silence, or words it could not make out: an empty sentence, which the caller treats as "nothing heard". */
                        else -> Speech.Final("")
                    },
                )
                close()
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName),
        )
        awaitClose {
            recognizer.cancel()
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main)

    private fun best(results: Bundle?): String? =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
}
