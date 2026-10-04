package com.karan.anuj.feature.voice.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** How the listening notice gets back into the app. */
object VoiceLinks {

    private const val REQUEST_OPEN = 4002

    /** Opens the app's main screen when the listening notice is tapped. */
    fun openApp(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, REQUEST_OPEN, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
