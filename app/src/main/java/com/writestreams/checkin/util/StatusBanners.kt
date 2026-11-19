package com.writestreams.checkin.util

import com.writestreams.checkin.service.SettingsService
import com.writestreams.checkin.service.SyncStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Wording for the banners shown at the top of every screen; null means no banner. */
object StatusBanners {

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d")

    // Every day of the week maps to the most recent Sunday's event, so this compares
    // events, not dates: a Saturday setup isn't flagged, a leftover or mis-picked date is.
    fun wrongEventText(selectedInstanceId: String, today: LocalDate): String? {
        val currentInstanceId = SettingsService.computeBreezeInstanceId(today)
        if (selectedInstanceId == currentInstanceId) return null
        val selected = SettingsService.instanceDate(selectedInstanceId)?.format(DATE_FORMAT)
            ?: "event $selectedInstanceId"
        val current = SettingsService.instanceDate(currentInstanceId)?.format(DATE_FORMAT)
        return "Checking in to $selected – not the current event. Tap to switch to $current."
    }

    // Shown only after a Breeze check has failed (not while the first check is running).
    fun offlineText(status: SyncStatus): String? {
        if (status.breezeReachable != false) return null
        val pending = if (status.pendingPushCount > 0) " (${status.pendingPushCount} pending)" else ""
        return "Breeze is unreachable. Verify that you're connected to a proper wifi, such as Kings Staff. " +
            "Check-ins are being saved on this tablet and will upload when Breeze is reachable$pending."
    }
}
