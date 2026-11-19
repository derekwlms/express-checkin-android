package com.writestreams.checkin.util

import com.writestreams.checkin.service.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatusBannersTest {

    private val sunday = LocalDate.of(2026, 11, 15)        // event 210398462
    private val wednesday = LocalDate.of(2026, 11, 18)     // still that Sunday's event
    private val saturday = LocalDate.of(2026, 11, 21)

    @Test
    fun currentEventShowsNoBannerAnyDayOfTheWeek() {
        assertNull(StatusBanners.wrongEventText("210398462", sunday))
        assertNull(StatusBanners.wrongEventText("210398462", wednesday))
        assertNull(StatusBanners.wrongEventText("210398462", saturday))
    }

    @Test
    fun leftoverEventFromLastWeekIsFlagged() {
        assertEquals(
            "Checking in to Sun, Nov 8 – not the current event. Tap to switch to Sun, Nov 15.",
            StatusBanners.wrongEventText("210398460", sunday)
        )
    }

    @Test
    fun nextWeeksEventPickedTooEarlyIsFlagged() {
        val text = StatusBanners.wrongEventText("210398464", saturday)!!
        assertTrue(text.startsWith("Checking in to Sun, Nov 22"))
    }

    @Test
    fun offlineBannerOnlyAfterAFailedCheck() {
        assertNull(StatusBanners.offlineText(SyncStatus(breezeReachable = null)))
        assertNull(StatusBanners.offlineText(SyncStatus(breezeReachable = true)))
        val text = StatusBanners.offlineText(SyncStatus(breezeReachable = false, pendingPushCount = 3))!!
        assertTrue(text.contains("such as Kings Staff"))
        assertTrue(text.endsWith("(3 pending)."))
    }
}
