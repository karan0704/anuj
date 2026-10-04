package com.karan.anuj.feature.voice.platform

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.karan.anuj.core.domain.voice.Speaker
import com.karan.anuj.core.domain.voice.Torch
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads answers aloud with the phone's own text-to-speech, which works
 * without the internet once its English voice is installed (it is on nearly
 * every phone). If the phone has no voice at all, [say] returns at once and
 * the answer is still on the screen.
 */
@Singleton
class AndroidSpeaker @Inject constructor(
    @ApplicationContext private val context: Context,
) : Speaker {

    private var engine: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null
    private var counter = 0

    override suspend fun say(text: String) {
        if (text.isBlank() || !prepare()) return
        val speech = engine ?: return
        val id = "anuj-${++counter}"
        /** A stuck or silenced engine must not hold the assistant forever. */
        withTimeoutOrNull(LONGEST_SPEECH_MILLIS) {
            suspendCancellableCoroutine { waiting ->
                speech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == id && waiting.isActive) waiting.resume(Unit)
                    }

                    @Deprecated("Replaced by the two-argument form, which the phone calls on newer versions")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id && waiting.isActive) waiting.resume(Unit)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId == id && waiting.isActive) waiting.resume(Unit)
                    }
                })
                if (speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS && waiting.isActive) {
                    waiting.resume(Unit)
                }
                waiting.invokeOnCancellation { speech.stop() }
            }
        }
    }

    override fun stop() {
        engine?.stop()
    }

    /** Starts the phone's speech engine the first time it is needed and waits for it to say whether it works. */
    private suspend fun prepare(): Boolean {
        ready?.let { return it.await() }
        val result = CompletableDeferred<Boolean>()
        ready = result
        engine = TextToSpeech(context) { status ->
            val works = status == TextToSpeech.SUCCESS &&
                (engine?.setLanguage(Locale.ENGLISH) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
            result.complete(works)
        }
        return withTimeoutOrNull(START_MILLIS) { result.await() } ?: false
    }

    private companion object {
        const val START_MILLIS = 5_000L
        const val LONGEST_SPEECH_MILLIS = 60_000L
    }
}

/** The flashlight on the back of the phone. Switching it needs no permission. */
@Singleton
class AndroidTorch @Inject constructor(
    @ApplicationContext private val context: Context,
) : Torch {

    override suspend fun set(on: Boolean): Boolean {
        val cameras = context.getSystemService(CameraManager::class.java) ?: return false
        return try {
            val withFlash = cameras.cameraIdList.firstOrNull { id ->
                val traits = cameras.getCameraCharacteristics(id)
                traits.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    traits.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return false
            cameras.setTorchMode(withFlash, on)
            true
        } catch (inUse: CameraAccessException) {
            /** Another app has the camera open, so the phone refuses; the command says so. */
            false
        }
    }
}
