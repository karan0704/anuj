package com.karan.anuj.core.domain

import com.karan.anuj.core.domain.history.RecordChange
import com.karan.anuj.core.domain.history.diffFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldDiffTest {

    @Test
    fun `identical rows produce no changes`() {
        val row = mapOf("name" to "Buy milk", "note" to null)

        assertTrue(diffFields("task", "1", row, row, changedAt = 10).isEmpty())
    }

    @Test
    fun `only the fields that differ are reported with old and new values`() {
        val before = mapOf("name" to "Buy milk", "priority" to "LOW")
        val after = mapOf("name" to "Buy oat milk", "priority" to "LOW")

        val changes = diffFields("task", "1", before, after, changedAt = 10)

        assertEquals(
            listOf(RecordChange("task", "1", "name", "Buy milk", "Buy oat milk", 10)),
            changes,
        )
    }

    @Test
    fun `a field that appears or disappears is a change to or from null`() {
        val before = mapOf("name" to "Sleep", "note" to "old note")
        val after = mapOf("name" to "Sleep", "tag" to "health")

        val changes = diffFields("task", "7", before, after, changedAt = 20)

        assertEquals(
            listOf(
                RecordChange("task", "7", "note", "old note", null, 20),
                RecordChange("task", "7", "tag", null, "health", 20),
            ),
            changes,
        )
    }
}
