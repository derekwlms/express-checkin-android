package com.writestreams.checkin.service

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.writestreams.checkin.data.network.MailgunService
import com.writestreams.checkin.util.ApiKeys
import com.writestreams.checkin.util.ApiKeys.MAILGUN_URL
import com.writestreams.checkin.util.AttendanceLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

class AttendanceService(private val context: Context) {

    private val bluetoothPrintService = BluetoothPrintService(context)
    private val mailgunService: MailgunService

    init {
        val client = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(MAILGUN_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        mailgunService = retrofit.create(MailgunService::class.java)
    }

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
        val dateTime = DateTimeFormatter.ofPattern("MMM d, yyyy (h:mm a)").format(LocalDateTime.now())
        val combinedList = attendanceList.joinToString(separator = "\n")
        val credentials = Base64.getEncoder().encodeToString(ApiKeys.MAILGUN_API_KEY.toByteArray())
        val authorization = "Basic $credentials"
        val htmlContent = combinedList

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = mailgunService.sendEmail(
                    authorization,
                    "Express Check-in <cmcheckin@sgcatlanta.org>",
                    recipient,
                    "SGC Children's Ministry - Attendance List - $dateTime",
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
}