package com.karan.anuj.core.domain

import com.karan.anuj.core.domain.record.RecordStamps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordStampsTest {

    @Test
    fun `a new row is created and updated at the same instant and is not deleted`() {
        val stamps = RecordStamps.created(now = 100)

        assertEquals(100, stamps.createdAt)
        assertEquals(100, stamps.updatedAt)
        assertFalse(stamps.isDeleted)
    }

    @Test
    fun `editing moves updatedAt and keeps createdAt`() {
        val stamps = RecordStamps.created(now = 100).touched(now = 250)

        assertEquals(100, stamps.createdAt)
        assertEquals(250, stamps.updatedAt)
    }

    @Test
    fun `deleting marks the row and also counts as an update`() {
        val stamps = RecordStamps.created(now = 100).deleted(now = 300)

        assertTrue(stamps.isDeleted)
        assertEquals(300L, stamps.deletedAt)
        assertEquals(300, stamps.updatedAt)
    }

    @Test
    fun `restoring clears the deletion and keeps the original creation time`() {
        val stamps = RecordStamps.created(now = 100).deleted(now = 300).restored(now = 400)

        assertFalse(stamps.isDeleted)
        assertNull(stamps.deletedAt)
        assertEquals(100, stamps.createdAt)
        assertEquals(400, stamps.updatedAt)
    }
}
