package com.writestreams.checkin.service

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.data.network.BreezeChmsApiService
import com.writestreams.checkin.data.network.MailgunService
import com.writestreams.checkin.data.network.NetworkClients
import com.writestreams.checkin.util.ApiKeys
import com.writestreams.checkin.util.AppScope
import com.writestreams.checkin.util.AttendanceLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

class AttendanceService(private val context: Context) {

    private val breezeApiService: BreezeChmsApiService = NetworkClients.breezeApiService
    private val bluetoothPrintService = BluetoothPrintService(context)
    private val mailgunService: MailgunService = NetworkClients.mailgunService

    fun printAttendanceList(attendanceList: List<String>) {
        val dateTime = DateTimeFormatter.ofPattern("MMM d, yyyy (h:mm a)").format(LocalDateTime.now())
        val chunkedAttendanceList = attendanceList.chunked(16)
        AppScope.io.launch {
            chunkedAttendanceList.forEachIndexed { index, chunk ->
                val label = AttendanceLabel(dateTime, chunk.size.toString(), chunk, isContinuation = index > 0)
                bluetoothPrintService.printLabel(label)
            }
        }
        Toast.makeText(context, "Printed the attendance list", Toast.LENGTH_SHORT).show()
    }

    fun emailAttendanceList(attendanceList: List<String>, recipient: String) {
        val date = DateTimeFormatter.ofPattern("MMM d, yyyy").format(LocalDateTime.now())
        val breezeInstanceId = getBreezeInstanceId()
        val combinedList = " " + attendanceList.sorted().joinToString(separator = "\n")
        val htmlContent = " " + attendanceList.sorted().joinToString(separator = "<br />")
        val credentials = Base64.getEncoder().encodeToString(ApiKeys.MAILGUN_API_KEY.toByteArray())
        val authorization = "Basic $credentials"

        AppScope.io.launch {
            try {
                val response = mailgunService.sendEmail(
                    authorization,
                    "Express Check-in <cmcheckin@sgcatlanta.org>",
                    recipient,
                    "SGC Children's Ministry - Attendance List - $date - $breezeInstanceId",
                    combinedList,
                    htmlContent
                ).execute()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Emailed the attendance list", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to email the attendance list", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error emailing attendance list: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("AttendanceService.emailAttendanceList exception", e.message, e)
            }
        }
    }

    suspend fun getBreezeCheckedInPersons(): List<Person> {
        val instanceId = getBreezeInstanceId()
        return withContext(Dispatchers.IO) {
            try {
                val response = breezeApiService.getAttendance(instanceId)
                if (response.isSuccessful) {
                    response.body()?.map { it.asPerson() } ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("getBreezeCheckedInPersons - breezeApiService.getAttendance",
                    "Exception getting checked-in persons, probably offline", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error getting Breeze data. Is it offline?", Toast.LENGTH_SHORT).show()
                }
                emptyList()
            }
        }
    }

    private fun getBreezeInstanceId(): String {
        return SettingsService.currentBreezeInstanceId(context)
    }
}