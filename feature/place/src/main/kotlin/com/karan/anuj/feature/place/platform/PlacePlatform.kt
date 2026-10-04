package com.karan.anuj.feature.place.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.karan.anuj.core.domain.place.LocationFix
import com.karan.anuj.core.domain.place.LocationSource
import com.karan.anuj.core.domain.place.PlaceActions
import com.karan.anuj.core.domain.place.TrackPlacesUseCase
import com.karan.anuj.core.domain.reminder.SyncRemindersUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where the phone is. Google's location service is asked first: it answers
 * fastest and works indoors when it has anything to go on. When it is
 * missing, switched off or gives nothing, the phone's own GPS is asked.
 * Neither needs the internet, Wi-Fi or Bluetooth.
 */
@Singleton
class PhoneLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationSource {

    override val permitted: Boolean
        get() = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Needed for a check while the app is closed. Before Android 10 the ordinary permission covered it. */
    val permittedInBackground: Boolean
        get() = permitted &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION))

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun current(): LocationFix? {
        if (!permitted) return null
        return fromGoogle() ?: fromGps()
    }

    @SuppressLint("MissingPermission")
    private suspend fun fromGoogle(): LocationFix? {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) return null
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(MAX_AGE_MILLIS)
            .setDurationMillis(GOOGLE_WAIT_MILLIS)
            .build()
        return withTimeoutOrNull(GOOGLE_WAIT_MILLIS + GRACE_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val cancel = CancellationTokenSource()
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(request, cancel.token)
                    .addOnSuccessListener { location -> continuation.resume(location?.toFix()) }
                    .addOnFailureListener { continuation.resume(null) }
                continuation.invokeOnCancellation { cancel.cancel() }
            }
        }
    }

    /** A cold GPS start outdoors can take half a minute; indoors it may never answer, which is why this gives up. */
    @SuppressLint("MissingPermission")
    private suspend fun fromGps(): LocationFix? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return null
        return withTimeoutOrNull(GPS_WAIT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val cancel = CancellationSignal()
                LocationManagerCompat.getCurrentLocation(
                    manager, LocationManager.GPS_PROVIDER, cancel, ContextCompat.getMainExecutor(context),
                ) { location -> continuation.resume(location?.toFix()) }
                continuation.invokeOnCancellation { cancel.cancel() }
            }
        }
    }

    private fun Location.toFix() = LocationFix(latitude, longitude, if (hasAccuracy()) accuracy else UNKNOWN_ACCURACY, time)

    private companion object {
        /** A reading up to a minute old is as good as a fresh one for a 100 metre circle. */
        const val MAX_AGE_MILLIS = 60_000L
        const val GOOGLE_WAIT_MILLIS = 20_000L
        const val GRACE_MILLIS = 2_000L
        const val GPS_WAIT_MILLIS = 45_000L

        /** A reading that does not say how good it is is treated as a poor one. */
        const val UNKNOWN_ACCURACY = 200f
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaceModule {
    @Binds
    abstract fun bindLocationSource(impl: PhoneLocationSource): LocationSource
}

/**
 * Runs the place check: read where the phone is, hand it to the rules, and
 * let the reminder engine show whatever the rules asked for.
 *
 * A check runs whenever the app comes into view. With places switched on
 * and background location allowed, the phone is also asked to run one about
 * every fifteen minutes while the app is closed, which is the shortest
 * interval the phone allows for work it schedules itself.
 */
@Singleton
class PlaceRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: PhoneLocationSource,
    private val actions: PlaceActions,
    private val track: TrackPlacesUseCase,
    private val syncReminders: SyncRemindersUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Runs a change to places. Use this instead of a screen's own scope, which ends when the screen closes. */
    fun launch(block: suspend () -> Unit): Job = scope.launch { block() }

    val permitted: Boolean get() = source.permitted
    val permittedInBackground: Boolean get() = source.permittedInBackground

    fun onForeground(): Job = scope.launch {
        refreshSchedule()
        check()
    }

    /** @return false when no reading could be had */
    suspend fun check(): Boolean {
        if (!actions.observeSettings().first().enabled) return false
        val fix = source.current() ?: return false
        track.onFix(fix)
        syncReminders()
        return true
    }

    /** Keeps the background check in step with the switch and the permission. */
    suspend fun refreshSchedule() {
        val wanted = actions.observeSettings().first().enabled && source.permittedInBackground
        val work = WorkManager.getInstance(context)
        if (wanted) {
            work.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PlaceWorker>(CHECK_EVERY_MINUTES, TimeUnit.MINUTES).build(),
            )
        } else {
            work.cancelUniqueWork(WORK_NAME)
        }
    }

    private companion object {
        const val WORK_NAME = "anuj-place-check"
        const val CHECK_EVERY_MINUTES = 15L
    }
}

/** The background check. A check with no reading is not an error: the next one will try again. */
class PlaceWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    /** The system creates workers itself, so the runner is fetched from Hilt rather than injected. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun placeRunner(): PlaceRunner
    }

    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).placeRunner().check()
        return Result.success()
    }
}
