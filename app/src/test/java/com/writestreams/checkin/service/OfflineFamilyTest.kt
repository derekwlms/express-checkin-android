package com.writestreams.checkin.service

import com.writestreams.checkin.data.local.Guest
import com.writestreams.checkin.data.local.GuestChild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineFamilyTest {

    private fun guest(parentId: String, vararg childIds: String) = Guest(
        firstName = "Michael", lastName = "Garofalo", phoneNumber = "", emailAddress = "",
        dateOfBirth = null, breezeId = parentId,
        children = childIds.mapIndexed { i, id -> GuestChild("Kid$i", "Garofalo", null, breezeId = id) }
    )

    @Test
    fun guestWithoutChildrenHasNoFamilyLink() {
        assertTrue(OfflineFamily.forGuest(guest("OL_1")).isEmpty())
    }

    @Test
    fun guestFamilyHasParentFirstAsHeadOfHousehold() {
        val family = OfflineFamily.forGuest(guest("OL_1", "59493056", "OL_2_Kid1"))
        assertEquals(listOf("OL_1", "59493056", "OL_2_Kid1"), family.map { it.person_id })
        assertEquals(listOf("4", "2", "2"), family.map { it.family_role_id })
    }

    @Test
    fun parentRejectedButChildrenPushedIsReadyOnceParentIsPushed() {
        val family = OfflineFamily.forGuest(guest("OL_1", "59493056", "59493057"))
        assertFalse(OfflineFamily.isReadyToLink(family))
        val remapped = OfflineFamily.remap(family, "OL_1", "59581862")
        assertTrue(OfflineFamily.isReadyToLink(remapped))
        assertEquals("59581862", remapped.first().details.id)
        assertEquals(listOf("59581862", "59493056", "59493057"), OfflineFamily.orderedIds(remapped))
    }

    @Test
    fun wholeFamilyOfflineIsReadyOnlyAfterEveryMemberIsPushed() {
        val family = OfflineFamily.forGuest(guest("OL_1", "OL_2_Kid0", "OL_3_Kid1"))
        val partly = OfflineFamily.remapAll(family, mapOf("OL_1" to "100", "OL_2_Kid0" to "101"))
        assertFalse(OfflineFamily.isReadyToLink(partly))
        val all = OfflineFamily.remap(partly, "OL_3_Kid1", "102")
        assertTrue(OfflineFamily.isReadyToLink(all))
    }

    @Test
    fun orderedIdsPutsParentsBeforeChildren() {
        val family = OfflineFamily.forGuest(guest("100", "101")).reversed()
        assertEquals(listOf("100", "101"), OfflineFamily.orderedIds(family))
    }
}
