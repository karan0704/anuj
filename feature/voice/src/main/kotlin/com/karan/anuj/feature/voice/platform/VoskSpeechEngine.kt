package com.karan.anuj.feature.voice.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.karan.anuj.core.domain.voice.Speech
import com.karan.anuj.core.domain.voice.SpeechEngine
import com.karan.anuj.core.domain.voice.SpeechFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Speech to text with Vosk, entirely on the phone.
 *
 * The model ships inside the app as assets. Vosk reads plain files, so the
 * first use copies the model out to the app's private storage, once per
 * version of the app's model. Loading it takes a second or two and a good
 * share of memory, so one loaded model is shared by everything that listens.
 */
@Singleton
class VoskSpeechEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechEngine {

    private val loading = Mutex()
    private var model: Model? = null

    override fun listen(): Flow<Speech> = flow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            emit(Speech.Failed(SpeechFailure.NO_PERMISSION))
            return@flow
        }
        val loaded = loadModel()
        if (loaded == null) {
            emit(Speech.Failed(SpeechFailure.NO_MODEL))
            return@flow
        }
        val microphone = openMicrophone()
        if (microphone == null) {
            emit(Speech.Failed(SpeechFailure.MICROPHONE_BUSY))
            return@flow
        }
        val recognizer = Recognizer(loaded, SAMPLE_RATE.toFloat())
        try {
            microphone.startRecording()
            val buffer = ShortArray(BUFFER_SAMPLES)
            var lastPartial = ""
            while (coroutineContext.isActive) {
                val read = microphone.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                if (recognizer.acceptWaveForm(buffer, read)) {
                    val text = JSONObject(recognizer.result).optString("text")
                    lastPartial = ""
                    if (text.isNotBlank()) emit(Speech.Final(text))
                } else {
                    val partial = JSONObject(recognizer.partialResult).optString("partial")
                    if (partial != lastPartial) {
                        lastPartial = partial
                        if (partial.isNotBlank()) emit(Speech.Partial(partial))
                    }
                }
            }
        } finally {
            microphone.stop()
            microphone.release()
            recognizer.close()
        }
    }.flowOn(Dispatchers.IO)

    /** The permission is checked by [listen] before this is reached. */
    @SuppressLint("MissingPermission")
    private fun openMicrophone(): AudioRecord? {
        val smallest = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (smallest <= 0) return null
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(smallest, BUFFER_SAMPLES * 2),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        return record
    }

    private suspend fun loadModel(): Model? = loading.withLock {
        model ?: try {
            Model(unpackedModel().absolutePath).also { model = it }
        } catch (failure: IOException) {
            null
        }
    }

    /**
     * @return the folder holding the model as plain files, copied from the
     * assets if this version has not been copied before
     */
    @Throws(IOException::class)
    private fun unpackedModel(): File {
        val target = File(context.filesDir, ASSET_FOLDER)
        val marker = File(target, MARKER)
        if (marker.exists() && marker.readText() == MODEL_VERSION) return target
        target.deleteRecursively()
        copyAssets(ASSET_FOLDER, target)
        marker.writeText(MODEL_VERSION)
        return target
    }

    @Throws(IOException::class)
    private fun copyAssets(path: String, target: File) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } }
            return
        }
        target.mkdirs()
        children.forEach { copyAssets("$path/$it", File(target, it)) }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        /** A fifth of a second of sound at a time: short enough to feel live, long enough not to spin. */
        const val BUFFER_SAMPLES = 3_200
        const val ASSET_FOLDER = "speech-model"
        const val MARKER = "copied-version"
        /** Changed whenever the bundled model changes, so the old copy on the phone is replaced. */
        const val MODEL_VERSION = "small-en-us-0.15"
    }
}
