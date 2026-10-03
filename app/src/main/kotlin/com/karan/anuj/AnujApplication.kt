package com.karan.anuj

import android.app.Application
import com.karan.anuj.core.security.AppLockController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AnujApplication : Application() {

    @Inject
    lateinit var appLock: AppLockController

    override fun onCreate() {
        super.onCreate()
        appLock.attach()
    }
}
