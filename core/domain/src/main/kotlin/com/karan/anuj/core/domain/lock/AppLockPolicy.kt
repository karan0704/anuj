package com.karan.anuj.core.domain.lock

/**
 * Decides whether the app must ask for the fingerprint again.
 *
 * Leaving the app for a moment (answering a notification, checking another
 * app) should not cost an unlock, so the lock only comes back after the app
 * has been out of view for at least [graceMillis]. That is the delay used
 * when a caller gives none; the app passes the user's own setting.
 */
class AppLockPolicy(private val graceMillis: Long = DEFAULT_GRACE_MILLIS) {

    /**
     * @param backgroundedAt when the app last left the screen, or null if it
     * has not been shown yet in this process (a cold start always locks).
     */
    fun shouldLock(
        lockEnabled: Boolean,
        backgroundedAt: Long?,
        now: Long,
        graceMillis: Long = this.graceMillis,
    ): Boolean {
        if (!lockEnabled) return false
        if (backgroundedAt == null) return true
        return now - backgroundedAt >= graceMillis
    }

    companion object {
        const val DEFAULT_GRACE_MILLIS: Long = 60_000L
    }
}
