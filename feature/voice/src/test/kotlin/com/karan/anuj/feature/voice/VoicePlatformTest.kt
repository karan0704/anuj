package com.karan.anuj.feature.voice

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.karan.anuj.core.domain.voice.Speech
import com.karan.anuj.core.domain.voice.SpeechFailure
import com.karan.anuj.feature.voice.platform.AndroidTorch
import com.karan.anuj.feature.voice.platform.VoskSpeechEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The parts of the voice feature that can be exercised without a real
 * microphone: what happens when the phone says no.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoicePlatformTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `listening without the microphone permission says so instead of failing`() = runBlocking {
        shadowOf(context as Application).denyPermissions(Manifest.permission.RECORD_AUDIO)

        val first = VoskSpeechEngine(context).listen().first()

        assertEquals(Speech.Failed(SpeechFailure.NO_PERMISSION), first)
    }

    @Test
    fun `a phone with no flashlight answers no rather than crashing`() = runBlocking {
        assertFalse(AndroidTorch(context).set(true))
    }
}
