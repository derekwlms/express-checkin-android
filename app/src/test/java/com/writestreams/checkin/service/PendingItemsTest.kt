package com.writestreams.checkin.service

import com.writestreams.checkin.data.local.Checkin
import com.writestreams.checkin.data.local.Guest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PendingItemsTest {

    private val current = "210398462"   // Nov 15, 2026
    private val lastWeek = "210398460"  // Nov 8, 2026
    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 15, 9, 30)
    private val names = mapOf("100" to "Zane Garofalo", "200" to "Ann Smith")

    private fun checkin(personId: String, instanceId: String = current, checkedOut: Boolean = false) =
        Checkin(personId, instanceId, now, "1234", "1", null, if (checkedOut) now else null)

    private fun build(checkins: List<Checkin>, newPersonIds: List<String> = emptyList()) =
        PendingItems.build(
            checkins,
            newPersonIds.map {
                Guest("Michael", "Garofalo", "", "", null, emptyList(), breezeId = it).asPerson()
            },
            { names[it] },
            current
        )

    @Test
    fun nothingUnsentMeansEmpty() {
        assertTrue(build(emptyList()).isEmpty())
    }

    @Test
    fun newPersonWithoutCheckinIsListed() {
        // e.g. a guest parent: parents get no check-in row
        val items = build(emptyList(), listOf("OL_1"))
        assertEquals(1, items.size)
        assertEquals("Michael Garofalo", items[0].name)
        assertEquals("new person", items[0].detail)
    }

    @Test
    fun newPersonWithCheckinIsOneRow() {
        val items = build(listOf(checkin("OL_1")), listOf("OL_1"))
        assertEquals(listOf("new person", "check-in"), items.single().parts)
    }

    @Test
    fun checkoutsAndPastEventsAreLabeled() {
        val items = build(listOf(checkin("100", checkedOut = true), checkin("200", lastWeek)))
        assertEquals("Ann Smith", items[0].name)
        assertEquals("check-in from Nov 8", items[0].detail)
        assertEquals("check-out", items[1].detail)
    }

    @Test
    fun unknownPersonFallsBackToId() {
        assertEquals("999", build(listOf(checkin("999"))).single().name)
    }

    @Test
    fun rejectedItemsAreMarkedAndListedFirst() {
        val items = PendingItems.withRejections(
            build(listOf(checkin("100")), listOf("OL_1")), mapOf("OL_1" to "HTTP 400"))
        assertEquals("OL_1", items[0].personId)
        assertEquals("new person · rejected by Breeze: HTTP 400", items[0].detail)
        assertNull(items[1].rejection)
    }
}
