package com.writestreams.checkin.service

import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AttendanceService(private val context: Context) {

    private val bluetoothPrintService = BluetoothPrintService(context)

    fun printAttendanceList(attendanceList: List<String>) {
        val deviceAddress = "66:32:D7:D6:ED:10"
        val labelText = attendanceList.joinToString(separator = "\n")
        CoroutineScope(Dispatchers.IO).launch {
            bluetoothPrintService.printLabel(deviceAddress, labelText)
        }
        Toast.makeText(context, "Printed the attendance list", Toast.LENGTH_SHORT).show()
    }

    fun emailAttendanceList(attendanceList: List<String>, recipient: String) {
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, "SGC Children's Ministry - Attendance List")
            putExtra(Intent.EXTRA_TEXT, "The list will go here...")
        }
        context.startActivity(Intent.createChooser(emailIntent, "Email Attendance List"))
        Toast.makeText(context, "Emailed the attendance list", Toast.LENGTH_SHORT).show()
    }
}