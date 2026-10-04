package com.karan.anuj.feature.reminder.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karan.anuj.core.domain.reminder.AnswerReminderUseCase
import com.karan.anuj.core.domain.reminder.ReminderId
import com.karan.anuj.core.domain.reminder.ReminderRepository
import com.karan.anuj.core.domain.reminder.ReminderSettingsRepository
import com.karan.anuj.core.domain.reminder.SyncRemindersUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Where reminder work runs so that it finishes whatever happens to the
 * screen or the receiver that started it: it lives as long as the app's
 * process.
 *
 * It also keeps the next alarm true while the app is running. Any change to
 * a task, a reminder or the reminder settings re-runs the sync a moment
 * later, so no screen has to remember to reschedule anything.
 */
@Singleton
class ReminderRunner @Inject constructor(
    private val sync: SyncRemindersUseCase,
    private val reminders: ReminderRepository,
    private val settings: ReminderSettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var attached = false

    /** Called once when the process starts, whatever started it: the app icon, an alarm or a restart of the phone. */
    @OptIn(FlowPreview::class)
    fun attach() {
        if (attached) return
        attached = true
        scope.launch {
            merge(reminders.observeChanges(), settings.settings.map { })
                /** A burst of edits (ticking a routine's steps) becomes one sync, once things have settled. */
                .debounce(SETTLE_MILLIS)
                .collect { syncSafely() }
        }
    }

    fun syncNow(): Job = scope.launch { syncSafely() }

    /** Runs a change to reminders. Use this instead of a screen's own scope, which ends when the screen closes. */
    fun launch(block: suspend () -> Unit): Job = scope.launch { block() }

    /** Runs [block] for a broadcast and tells the phone when it is finished, so the phone stays awake until then. */
    fun runFor(broadcast: BroadcastReceiver.PendingResult, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                /** The next alarm, edit or app start runs the sync again; one failed run must not crash the process. */
            } finally {
                broadcast.finish()
            }
        }
    }

    suspend fun syncSafely() {
        try {
            sync()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            /** As above: a failed run is retried by whatever triggers the sync next. */
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 300L
    }
}

/** How the receivers below, which the phone creates itself, reach the app's objects. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun runner(): ReminderRunner
    fun answer(): AnswerReminderUseCase
}

private fun entryPoint(context: Context): ReminderEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)

/** The alarm going off: show what is due and set the next alarm. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val runner = entryPoint(context).runner()
        runner.runFor(goAsync()) { runner.syncSafely() }
    }
}

/**
 * The phone restarted, the app was updated, or the clock or time zone
 * changed. Each of these loses or misplaces the alarm, so it is set again.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val runner = entryPoint(context).runner()
        runner.runFor(goAsync()) { runner.syncSafely() }
    }
}

/** "Done" or the one-tap snooze, pressed on a notification with the app closed. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderLinks.EXTRA_REMINDER_ID)?.let(::ReminderId) ?: return
        val entry = entryPoint(context)
        entry.runner().runFor(goAsync()) {
            when (intent.action) {
                ReminderLinks.ACTION_DONE -> entry.answer().done(id)
                ReminderLinks.ACTION_SNOOZE ->
                    entry.answer().snooze(id, intent.getIntExtra(ReminderLinks.EXTRA_MINUTES, FALLBACK_SNOOZE_MINUTES))
            }
        }
    }

    private companion object {
        /** Only used if a notification somehow carries no length; every notification the app posts does. */
        const val FALLBACK_SNOOZE_MINUTES = 10
    }
}
