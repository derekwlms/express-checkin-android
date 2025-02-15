package com.writestreams.checkin.service

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.writestreams.checkin.util.AttendanceLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AttendanceService(private val context: Context) {

    private val bluetoothPrintService = BluetoothPrintService(context)

    fun printAttendanceList(attendanceList: List<String>) {
        val dateTime = DateTimeFormatter.ofPattern("MMM d, yyyy (h:mm a)").format(LocalDateTime.now())
        val chunkedAttendanceList = attendanceList.chunked(16)
        CoroutineScope(Dispatchers.IO).launch {
            chunkedAttendanceList.forEachIndexed { index, chunk ->
                val label = AttendanceLabel(dateTime, chunk.size.toString(), chunk, isContinuation = index > 0)
                bluetoothPrintService.printLabel(label)
            }
        }
        Toast.makeText(context, "Printed the attendance list", Toast.LENGTH_SHORT).show()
    }

    fun emailAttendanceList(attendanceList: List<String>, recipient: String) {
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, "SGC Children's Ministry - Attendance List")
            val combinedList = attendanceList.joinToString(separator = "\n")
            putExtra(Intent.EXTRA_TEXT, combinedList)
        }
        context.startActivity(Intent.createChooser(emailIntent, "Email Attendance List"))
        Toast.makeText(context, "Emailed the attendance list", Toast.LENGTH_SHORT).show()
    }
}