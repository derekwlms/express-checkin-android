package com.writestreams.checkin.service

import android.content.Context
import android.util.Log
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class SettingsService(private val context: Context) {

    companion object {
        private val BREEZE_INSTANCE_ID_START_DATE: LocalDate = LocalDate.of(2025, 2, 2)
        private const val BREEZE_INSTANCE_ID_START = 210398276   // Groundhog Day 2025

        private val deviceAddresses = mapOf(
            "Printer A" to "66:32:F6:7A:4D:65",   // 117
            "Printer B" to "66:32:D7:D6:ED:10",
            "Printer C" to "66:32:27:5A:91:A4",   // 514
            "Printer D" to "66:32:AF:39:15:16",   // 078
            "Printer E" to "10:23:81:47:79:F7"    // D450
        )

        private val checkinCounterLock = Any()

        fun computeBreezeInstanceId(date: LocalDate): String {
            val weeksFromStartDate = ChronoUnit.WEEKS.between(BREEZE_INSTANCE_ID_START_DATE, date).toInt()
            return (BREEZE_INSTANCE_ID_START + (2 * weeksFromStartDate)).toString()
        }

        // The single source for "which event instance is current" - preferring the
        // stored value (which the Settings date picker can override) over today's.
        fun currentBreezeInstanceId(context: Context): String {
            val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            return sharedPreferences.getString("breeze_instance_id", null)
                ?: computeBreezeInstanceId(LocalDate.now())
        }
    }

    fun updateBreezeInstanceId(date: LocalDate): String {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val breezeInstanceId = computeBreezeInstanceId(date)
        Log.d("SettingsService", "Updating Breeze instance ID to $breezeInstanceId for date date: $date")
        with(sharedPreferences.edit()) {
            putString("breeze_instance_id", breezeInstanceId)
            apply()
        }
        return breezeInstanceId
    }

    fun getBreezeInstanceId(): String? {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("breeze_instance_id", null)
    }

    // Tablet identity keeps check-in codes and counters from colliding when two
    // tablets run at once: both are prefixed with the tablet letter (e.g. "A4821").
    fun getTabletId(): String {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("tablet_id", "A") ?: "A"
    }

    fun updateTabletId(tabletId: String) {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("tablet_id", tabletId)
            apply()
        }
    }

    // Per-tablet, per-instance sequence number ("A7"). Resets automatically when
    // the event instance rolls over to a new Sunday.
    fun nextCheckinCounter(): String {
        synchronized(checkinCounterLock) {
            val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val currentInstanceId = currentBreezeInstanceId(context)
            val counterInstanceId = sharedPreferences.getString("checkin_counter_instance", null)
            val counter = if (counterInstanceId == currentInstanceId) {
                sharedPreferences.getInt("checkin_counter", 0) + 1
            } else {
                1
            }
            with(sharedPreferences.edit()) {
                putString("checkin_counter_instance", currentInstanceId)
                putInt("checkin_counter", counter)
                apply()
            }
            return getTabletId() + counter
        }
    }

    fun resetCheckinCounter() {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putInt("checkin_counter", 0)
            apply()
        }
    }

    fun updatePrinterDeviceAddress(printerName: String) {
        val deviceAddress =  deviceAddresses[printerName] ?: "66:32:F6:7A:4D:65"
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("printer_device_address", deviceAddress)
            apply()
        }
    }
}