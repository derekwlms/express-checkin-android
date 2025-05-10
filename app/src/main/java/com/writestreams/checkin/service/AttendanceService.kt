package com.writestreams.checkin.service

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.data.network.BreezeChmsApiService
import com.writestreams.checkin.data.network.MailgunService
import com.writestreams.checkin.util.ApiKeys
import com.writestreams.checkin.util.ApiKeys.BREEZE_API_URL
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

    private val breezeApiService: BreezeChmsApiService
    private val bluetoothPrintService = BluetoothPrintService(context)
    private val mailgunService: MailgunService

    init {
        val mailClient = OkHttpClient.Builder().build()
        val mailRetrofit = Retrofit.Builder()
            .baseUrl(MAILGUN_URL)
            .client(mailClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        mailgunService = mailRetrofit.create(MailgunService::class.java)

        val breezeApiClient = OkHttpClient.Builder().build()
        val breezeApiRetrofit = Retrofit.Builder()
            .baseUrl(BREEZE_API_URL)
            .client(breezeApiClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        breezeApiService = breezeApiRetrofit.create(BreezeChmsApiService::class.java)
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
        val date = DateTimeFormatter.ofPattern("MMM d, yyyy").format(LocalDateTime.now())
        val breezeInstanceId = getBreezeInstanceId()
        val combinedList = " " + attendanceList.sorted().joinToString(separator = "\n")
        val htmlContent = " " + attendanceList.sorted().joinToString(separator = "<br />")
        val credentials = Base64.getEncoder().encodeToString(ApiKeys.MAILGUN_API_KEY.toByteArray())
        val authorization = "Basic $credentials"

        CoroutineScope(Dispatchers.IO).launch {
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
            val response = breezeApiService.getAttendance(instanceId)
            if (response.isSuccessful) {
                response.body()?.map { it.asPerson() } ?: emptyList()
            } else {
                emptyList()
            }
        }
    }

    private fun getBreezeInstanceId(): String {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("breeze_instance_id", "210398284") ?: "210398284"
    }
}