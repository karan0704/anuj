package com.karan.anuj.core.domain

import com.karan.anuj.core.domain.lock.AppLockPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {

    private val policy = AppLockPolicy(graceMillis = 60_000)

    @Test
    fun `never locks when the lock is switched off`() {
        assertFalse(policy.shouldLock(lockEnabled = false, backgroundedAt = null, now = 0))
        assertFalse(policy.shouldLock(lockEnabled = false, backgroundedAt = 0, now = 10_000_000))
    }

    @Test
    fun `a cold start locks`() {
        assertTrue(policy.shouldLock(lockEnabled = true, backgroundedAt = null, now = 5_000))
    }

    @Test
    fun `a short trip out of the app does not lock`() {
        assertFalse(policy.shouldLock(lockEnabled = true, backgroundedAt = 1_000, now = 60_999))
    }

    @Test
    fun `a delay chosen by the user replaces the built-in one`() {
        assertTrue(policy.shouldLock(lockEnabled = true, backgroundedAt = 1_000, now = 1_000, graceMillis = 0))
        assertFalse(policy.shouldLock(lockEnabled = true, backgroundedAt = 1_000, now = 200_000, graceMillis = 300_000))
    }

    @Test
    fun `being away for the full grace period locks`() {
        assertTrue(policy.shouldLock(lockEnabled = true, backgroundedAt = 1_000, now = 61_000))
    }
}
