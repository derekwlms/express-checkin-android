package com.writestreams.checkin.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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

        fun computeBreezeInstanceId(date: LocalDate): String {
            val weeksFromStartDate = ChronoUnit.WEEKS.between(BREEZE_INSTANCE_ID_START_DATE, date).toInt()
            return (BREEZE_INSTANCE_ID_START + (2 * weeksFromStartDate)).toString()
        }

        // The Sunday an instance id belongs to (the inverse of computeBreezeInstanceId).
        fun instanceDate(instanceId: String): LocalDate? {
            val offset = instanceId.toIntOrNull()?.minus(BREEZE_INSTANCE_ID_START) ?: return null
            if (offset < 0 || offset % 2 != 0) return null
            return BREEZE_INSTANCE_ID_START_DATE.plusWeeks((offset / 2).toLong())
        }

        // The selected event, re-emitted whenever it changes (Settings, or the wrong-event banner)
        fun selectedEventFlow(context: Context): Flow<String> = callbackFlow {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "breeze_instance_id") trySend(currentBreezeInstanceId(context))
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            send(currentBreezeInstanceId(context))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()

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

    // The name of the saved printer (by its Bluetooth address), or null if it isn't a known one
    fun getPrinterName(): String? {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val address = sharedPreferences.getString("printer_device_address", deviceAddresses["Printer A"])
        return deviceAddresses.entries.firstOrNull { it.value == address }?.key
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