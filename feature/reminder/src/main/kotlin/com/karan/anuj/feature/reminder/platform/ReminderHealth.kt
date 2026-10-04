package com.karan.anuj.feature.reminder.platform

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import com.karan.anuj.feature.reminder.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One thing the phone must allow for reminders to arrive on time. */
enum class HealthCheck(@StringRes val title: Int, @StringRes val problem: Int) {
    NOTIFICATIONS(R.string.check_notifications, R.string.check_notifications_problem),
    EXACT_ALARMS(R.string.check_exact, R.string.check_exact_problem),
    BATTERY(R.string.check_battery, R.string.check_battery_problem),
    FULL_SCREEN(R.string.check_full_screen, R.string.check_full_screen_problem),
}

/**
 * @property fix the system screen where the user can allow it; null when
 * this version of Android has no such screen
 */
data class HealthItem(val check: HealthCheck, val ok: Boolean, val fix: Intent?)

/**
 * Reads what the phone currently allows. Nothing here changes a setting:
 * each problem comes with the system screen that fixes it, and the user
 * decides.
 */
@Singleton
class ReminderHealth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarms: AndroidAlarmGateway,
) {
    private val appAddress: Uri get() = Uri.parse("package:${context.packageName}")

    fun read(): List<HealthItem> = listOf(
        HealthItem(
            HealthCheck.NOTIFICATIONS,
            ok = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            fix = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        ),
        HealthItem(
            HealthCheck.EXACT_ALARMS,
            ok = alarms.canBeExact(),
            fix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, appAddress)
            } else {
                null
            },
        ),
        HealthItem(
            HealthCheck.BATTERY,
            ok = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            fix = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appAddress),
        ),
        HealthItem(
            HealthCheck.FULL_SCREEN,
            ok = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
            fix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, appAddress)
            } else {
                null
            },
        ),
    )

    /** This app's page in the phone's settings, where the maker-specific switches are found. */
    fun appSettings(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appAddress)

    /**
     * Some phone makers add their own battery savers that stop an app from
     * waking up, on top of Android's. The advice for this phone's maker, or
     * null when the maker is not known for it.
     */
    @StringRes
    fun makerTip(): Int? = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> R.string.maker_xiaomi
        "samsung" -> R.string.maker_samsung
        "oppo", "realme", "oneplus" -> R.string.maker_oppo
        "vivo", "iqoo" -> R.string.maker_vivo
        "huawei", "honor" -> R.string.maker_huawei
        else -> null
    }
}
