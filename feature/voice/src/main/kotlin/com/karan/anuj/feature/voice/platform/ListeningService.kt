package com.karan.anuj.feature.voice.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.karan.anuj.core.domain.voice.ListenWhen
import com.karan.anuj.core.domain.voice.Speech
import com.karan.anuj.core.domain.voice.TalkUseCase
import com.karan.anuj.core.domain.voice.VoiceSettings
import com.karan.anuj.core.domain.voice.VoiceSettingsUseCase
import com.karan.anuj.core.domain.voice.WakePhrase
import com.karan.anuj.feature.voice.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where voice work runs so that it finishes even if the sheet that started
 * it is closed: adding a task by voice must not be lost because the user
 * swiped the assistant away a moment too soon.
 *
 * It also starts and stops the listening service to match the "listen for
 * the name" setting.
 */
@Singleton
class VoiceRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: VoiceSettingsUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var attached = false

    /** Called once when the process starts. */
    fun attach() {
        if (attached) return
        attached = true
        scope.launch {
            settings.observe().map { it.listenWhen }.distinctUntilChanged().collect { refresh(it) }
        }
    }

    /** Called when the app comes to the front, the one moment the phone lets a microphone service start. */
    fun onForeground() {
        scope.launch { refresh(settings.observe().first().listenWhen) }
    }

    fun launch(block: suspend () -> Unit): Job = scope.launch { block() }

    val canUseMicrophone: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun refresh(listenWhen: ListenWhen) {
        val service = Intent(context, ListeningService::class.java)
        if (listenWhen == ListenWhen.NEVER || !canUseMicrophone) {
            context.stopService(service)
            return
        }
        try {
            ContextCompat.startForegroundService(context, service)
        } catch (notAllowed: IllegalStateException) {
            /**
             * The phone refuses to start a microphone service while the app
             * is in the background (after a restart, say). It starts the
             * next time the app is opened; [onForeground] sees to that.
             */
        }
    }
}

/**
 * Keeps the microphone open and waits for the assistant's name.
 *
 * "Anuj, what's next" is answered at once. The name alone gets a short beep
 * and the next sentence is taken as the command. While the answer is being
 * read aloud, what the microphone hears is thrown away, so the assistant
 * does not answer its own voice.
 */
@AndroidEntryPoint
class ListeningService : Service() {

    @Inject lateinit var talk: TalkUseCase
    @Inject lateinit var settings: VoiceSettingsUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Bumped whenever the screen or the charger changes, so the "may I listen" question is asked again. */
    private val phoneChanged = MutableStateFlow(0)

    private val phoneWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            phoneChanged.value += 1
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!showNotification()) {
            stopSelf()
            return
        }
        val events = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, phoneWatcher, events, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            combine(settings.observe(), phoneChanged) { current, _ -> current.takeIf(::mayListenNow) }
                .distinctUntilChanged()
                .collectLatest { current -> if (current != null) listenForName() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        try {
            unregisterReceiver(phoneWatcher)
        } catch (neverRegistered: IllegalArgumentException) {
            /** The service was stopped before it got as far as registering. */
        }
        super.onDestroy()
    }

    private fun mayListenNow(current: VoiceSettings): Boolean = when (current.listenWhen) {
        ListenWhen.NEVER -> false
        ListenWhen.ALWAYS -> true
        ListenWhen.WHILE_SCREEN_ON -> getSystemService(PowerManager::class.java)?.isInteractive == true
        ListenWhen.WHILE_CHARGING -> getSystemService(BatteryManager::class.java)?.isCharging == true
    }

    /**
     * Waits for the name with the light listener, then lets go of the
     * microphone, beeps, and takes the sentence down with the phone's better
     * recogniser. Only one of the two can hold the microphone at a time,
     * which is why this is a loop of two steps and not two things running
     * side by side.
     *
     * The first version did both jobs with the bundled model and one open
     * microphone. On a real phone it misheard the name and the command:
     *
     *     talk.listen().collect { heard ->
     *         if (heard !is Speech.Final || speaking) return@collect
     *         val found = WakePhrase.find(heard.text, current)
     *         ...
     *         talk.say(command)
     *     }
     */
    private suspend fun listenForName() {
        while (true) {
            val current = settings.observe().first()
            talk.listenForName(current.wakePhrases).first { heard ->
                heard is Speech.Failed || (heard is Speech.Final && WakePhrase.find(heard.text, current).woke)
            }.let { ended -> if (ended is Speech.Failed) return }

            beep()
            val wait = current.listenSeconds * MILLIS_PER_SECOND
            val sentence = withTimeoutOrNull(wait) { talk.listen().first { it !is Speech.Partial } }
            val command = (sentence as? Speech.Final)?.text.orEmpty()
            if (command.isNotBlank()) talk.say(command)
        }
    }

    private fun beep() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, BEEP_VOLUME)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MILLIS)
        scope.launch {
            kotlinx.coroutines.delay(BEEP_MILLIS * 2L)
            tone.release()
        }
    }

    /** @return false when the phone would not let the service run in the foreground */
    private fun showNotification(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.voice_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.voice_channel_detail)
                setShowBadge(false)
            },
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.voice_listening_title))
            .setContentText(getString(R.string.voice_listening_text))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(VoiceLinks.openApp(this))
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            true
        } catch (refused: SecurityException) {
            false
        } catch (refused: IllegalStateException) {
            false
        }
    }

    private companion object {
        const val CHANNEL = "assistant_listening"
        const val NOTIFICATION_ID = 4001
        const val MILLIS_PER_SECOND = 1_000L
        const val BEEP_VOLUME = 60
        const val BEEP_MILLIS = 150
    }
}
