package com.writestreams.checkin.service

import com.writestreams.checkin.data.local.FamilyMember
import com.writestreams.checkin.data.local.FamilyMemberDetails
import com.writestreams.checkin.data.local.Guest
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.service.CheckinService.Companion.FAMILY_ROLE_CHILD
import com.writestreams.checkin.service.CheckinService.Companion.FAMILY_ROLE_HEAD_OF_HOUSEHOLD
import com.writestreams.checkin.service.CheckinService.Companion.OFFLINE_BREEZE_ID_PREFIX

/**
 * Family links for people saved locally before Breeze accepted them (OL_ ids).
 *
 * The link is kept in each offline person's Breeze-shaped family list, so the family
 * can be checked in together while still offline. As members are pushed, their OL_ ids
 * in these lists are remapped to Breeze ids; once every member has a Breeze id, the
 * family is linked in Breeze.
 */
object OfflineFamily {

    // Parent (head of household) plus children; ids must already be assigned.
    fun forGuest(guest: Guest): List<FamilyMember> {
        if (guest.children.isEmpty()) return emptyList()
        return listOf(member(guest.breezeId, guest.firstName, guest.lastName, FAMILY_ROLE_HEAD_OF_HOUSEHOLD)) +
            guest.children.map { member(it.breezeId, it.firstName, it.lastName, FAMILY_ROLE_CHILD) }
    }

    // A child added (offline) to an existing parent's family.
    fun forNewChild(child: Guest, parent: Person): List<FamilyMember> {
        val parentRole = parent.family.firstOrNull { it.person_id == parent.id }?.family_role_id
            ?: FAMILY_ROLE_HEAD_OF_HOUSEHOLD
        return listOf(
            member(parent.id, parent.first_name, parent.last_name, parentRole),
            member(child.breezeId, child.firstName, child.lastName, FAMILY_ROLE_CHILD)
        )
    }

    fun remap(family: List<FamilyMember>, oldId: String, newId: String): List<FamilyMember> =
        family.map {
            if (it.person_id == oldId) it.copy(person_id = newId, details = it.details.copy(id = newId)) else it
        }

    fun remapAll(family: List<FamilyMember>, idMap: Map<String, String>): List<FamilyMember> =
        idMap.entries.fold(family) { f, (oldId, newId) -> remap(f, oldId, newId) }

    fun isReadyToLink(family: List<FamilyMember>): Boolean =
        family.map { it.person_id }.distinct().size >= 2 &&
            family.none { it.person_id.isEmpty() || it.person_id.startsWith(OFFLINE_BREEZE_ID_PREFIX) }

    // Heads of household first, so a newly created Breeze family is anchored on the parent.
    fun orderedIds(family: List<FamilyMember>): List<String> =
        family.sortedBy { if (it.family_role_id == FAMILY_ROLE_CHILD) 1 else 0 }
            .map { it.person_id }
            .distinct()

    fun names(family: List<FamilyMember>): String =
        family.joinToString(", ") { "${it.details.first_name} ${it.details.last_name}" }

    private fun member(personId: String, firstName: String, lastName: String, roleId: String) =
        FamilyMember(
            id = "",
            oid = "",
            person_id = personId,
            family_id = "",
            family_role_id = roleId,
            created_on = "",
            role_name = if (roleId == FAMILY_ROLE_CHILD) "Child" else "Head of Household",
            role_id = roleId,
            order = "",
            details = FamilyMemberDetails(personId, firstName, "", lastName, "", ""),
            checkinDateTime = null
        )
}
