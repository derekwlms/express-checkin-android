package com.writestreams.checkin.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SyncEngineTest {

    @Test
    fun blankOrZeroCheckoutMeansStillCheckedIn() {
        assertFalse(SyncEngine.isCheckedOut(null))
        assertFalse(SyncEngine.isCheckedOut(""))
        assertFalse(SyncEngine.isCheckedOut("0000-00-00 00:00:00"))
    }

    @Test
    fun realCheckoutTimestampMeansCheckedOut() {
        assertTrue(SyncEngine.isCheckedOut("2026-07-19 11:45:00"))
    }

    @Test
    fun parsesBreezeDateTimeFormat() {
        assertEquals(
            LocalDateTime.of(2026, 7, 19, 9, 30, 15),
            SyncEngine.parseBreezeDateTime("2026-07-19 09:30:15")
        )
    }

    @Test
    fun unparsableDateTimeReturnsNull() {
        assertNull(SyncEngine.parseBreezeDateTime(null))
        assertNull(SyncEngine.parseBreezeDateTime(""))
        assertNull(SyncEngine.parseBreezeDateTime("not a date"))
    }
}
