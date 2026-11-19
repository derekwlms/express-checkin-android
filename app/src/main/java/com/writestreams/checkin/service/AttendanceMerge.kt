package com.writestreams.checkin.service

import com.writestreams.checkin.data.local.Checkin

/**
 * Reconcile the local checkins table with Breeze's attendance list for the current event instance
 */
object AttendanceMerge {

    data class Actions(
        val markSynced: List<Checkin>,   // local pending rows Breeze already has
        val remove: List<Checkin>,       // local rows no longer in Breeze: checked out remotely, or a
                                         // local check-out that needs no push
        val record: List<String>,        // known person ids to record as checked in locally
        val fetch: List<String>          // unknown person ids to fetch from Breeze, then record
    )

    fun plan(
        localCheckins: List<Checkin>,
        localPersonIds: Set<String>,
        breezeCheckedInIds: Set<String>
    ): Actions {
        val localCheckinIds = localCheckins.map { it.personId }.toSet()
        val markSynced = localCheckins.filter {
            it.breezeSyncDateTime == null && it.checkoutDateTime == null && it.personId in breezeCheckedInIds
        }
        // Rows checked out here stay until Breeze no longer has the person; unsent
        // check-ins (the outbox) are never removed here.
        val remove = localCheckins.filter {
            it.personId !in breezeCheckedInIds && (it.breezeSyncDateTime != null || it.checkoutDateTime != null)
        }
        val newIds = breezeCheckedInIds.filterNot { it in localCheckinIds }
        return Actions(
            markSynced = markSynced,
            remove = remove,
            record = newIds.filter { it in localPersonIds },
            fetch = newIds.filterNot { it in localPersonIds }
        )
    }
}
