package com.karan.anuj.core.domain

import com.karan.anuj.core.domain.history.ChangeHistoryRepository
import com.karan.anuj.core.domain.history.RecordChange
import com.karan.anuj.core.domain.settings.FakeSettingsRepository
import com.karan.anuj.core.domain.settings.TextScale
import com.karan.anuj.core.domain.settings.ThemeMode
import com.karan.anuj.core.domain.settings.UpdateSettingUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSettingUseCaseTest {

    private val settings = FakeSettingsRepository()
    private val history = FakeHistoryRepository()
    private val update = UpdateSettingUseCase(settings, history, time = { 1_000L })

    @Test
    fun `changing a setting stores it and records what it was before`() = runTest {
        update.themeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, settings.state.value.themeMode)
        assertEquals(
            listOf(RecordChange("settings", "app", "themeMode", "SYSTEM", "DARK", 1_000L)),
            history.recorded,
        )
    }

    @Test
    fun `setting the value it already has records nothing`() = runTest {
        update.textScale(TextScale.NORMAL)

        assertTrue(history.recorded.isEmpty())
    }

    private class FakeHistoryRepository : ChangeHistoryRepository {
        val recorded = mutableListOf<RecordChange>()

        override suspend fun record(changes: List<RecordChange>) {
            recorded += changes
        }

        override fun observeFor(table: String, rowId: String): Flow<List<RecordChange>> = emptyFlow()
        override fun observeRecent(limit: Int): Flow<List<RecordChange>> = emptyFlow()
        override suspend fun deleteBefore(millis: Long) = Unit
    }
}
