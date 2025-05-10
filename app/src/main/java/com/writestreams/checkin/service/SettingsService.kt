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
    }

    fun updateBreezeInstanceId(date: LocalDate) {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val weeksFromStartDate = ChronoUnit.WEEKS.between(BREEZE_INSTANCE_ID_START_DATE, date).toInt()
        val breezeInstanceId = (BREEZE_INSTANCE_ID_START + (2 * weeksFromStartDate)).toString()
        Log.d("SettingsService", "Updating Breeze instance ID to $breezeInstanceId for date date: $date")
        with(sharedPreferences.edit()) {
            putString("breeze_instance_id", breezeInstanceId)
            apply()
        }
    }

    fun getBreezeInstanceId(): String? {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("breeze_instance_id", null)
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