package com.karan.anuj

import android.app.Application
import com.karan.anuj.core.security.AppLockController
import com.karan.anuj.feature.reminder.platform.ReminderRunner
import com.karan.anuj.feature.voice.platform.VoiceRunner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AnujApplication : Application() {

    @Inject
    lateinit var appLock: AppLockController

    @Inject
    lateinit var reminders: ReminderRunner

    @Inject
    lateinit var voice: VoiceRunner

    /**
     * The process can be started by the app icon, by a reminder's alarm or
     * by the phone restarting. In every case the reminder engine is started
     * here, so the next alarm is always set from the stored data.
     */
    override fun onCreate() {
        super.onCreate()
        appLock.attach()
        reminders.attach()
        /** Starts or stops listening for the assistant's name whenever that setting changes. */
        voice.attach()
    }
}
