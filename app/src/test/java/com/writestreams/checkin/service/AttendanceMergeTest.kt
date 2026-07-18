package com.writestreams.checkin.service

import com.writestreams.checkin.data.local.Checkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AttendanceMergeTest {

    private val instanceId = "210398428"
    private val now: LocalDateTime = LocalDateTime.of(2026, 7, 19, 9, 30)

    private fun pending(personId: String) =
        Checkin(personId, instanceId, now, "1234", "1")

    private fun synced(personId: String) =
        Checkin(personId, instanceId, now, "1234", "2", breezeSyncDateTime = now)

    @Test
    fun pendingRowPresentInBreezeIsMarkedSynced() {
        val row = pending("100")
        val actions = AttendanceMerge.plan(listOf(row), setOf("100"), setOf("100"))
        assertEquals(listOf(row), actions.markSynced)
        assertTrue(actions.remove.isEmpty())
    }

    @Test
    fun pendingRowAbsentFromBreezeIsLeftAloneAsOutbox() {
        val row = pending("OL_175170000_Pebbles")
        val actions = AttendanceMerge.plan(listOf(row), setOf("100"), setOf("100"))
        assertTrue(actions.markSynced.isEmpty())
        assertTrue(actions.remove.isEmpty())
    }

    @Test
    fun syncedRowAbsentFromBreezeIsRemovedAsRemoteCheckout() {
        val row = synced("100")
        val actions = AttendanceMerge.plan(listOf(row), setOf("100"), emptySet())
        assertEquals(listOf(row), actions.remove)
    }

    @Test
    fun syncedRowStillPresentInBreezeIsUntouched() {
        val row = synced("100")
        val actions = AttendanceMerge.plan(listOf(row), setOf("100"), setOf("100"))
        assertTrue(actions.markSynced.isEmpty())
        assertTrue(actions.remove.isEmpty())
        assertTrue(actions.record.isEmpty())
    }

    @Test
    fun breezeCheckinForKnownPersonIsRecordedLocally() {
        val actions = AttendanceMerge.plan(emptyList(), setOf("200"), setOf("200"))
        assertEquals(listOf("200"), actions.record)
        assertTrue(actions.fetch.isEmpty())
    }

    @Test
    fun breezeCheckinForUnknownPersonIsFetched() {
        // e.g. a guest family added on the other tablet
        val actions = AttendanceMerge.plan(emptyList(), setOf("200"), setOf("999"))
        assertEquals(listOf("999"), actions.fetch)
        assertTrue(actions.record.isEmpty())
    }
}
