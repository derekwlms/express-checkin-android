package com.writestreams.checkin.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SettingsServiceTest {

    @Test
    fun instanceIdForStartDate() {
        // Groundhog Day 2025, the seed Sunday
        assertEquals("210398276", SettingsService.computeBreezeInstanceId(LocalDate.of(2025, 2, 2)))
    }

    @Test
    fun instanceIdAdvancesByTwoEachWeek() {
        assertEquals("210398278", SettingsService.computeBreezeInstanceId(LocalDate.of(2025, 2, 9)))
        assertEquals("210398280", SettingsService.computeBreezeInstanceId(LocalDate.of(2025, 2, 16)))
    }

    @Test
    fun midWeekDatesMapToPrecedingSundayInstance() {
        // Wednesday Feb 12 belongs to the week that started Sunday Feb 9
        assertEquals("210398278", SettingsService.computeBreezeInstanceId(LocalDate.of(2025, 2, 12)))
        // Saturday Feb 8 still belongs to the week that started Sunday Feb 2
        assertEquals("210398276", SettingsService.computeBreezeInstanceId(LocalDate.of(2025, 2, 8)))
    }
}
