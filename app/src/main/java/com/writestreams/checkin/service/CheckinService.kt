package com.writestreams.checkin.service

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.writestreams.checkin.data.local.FamilyMember
import com.writestreams.checkin.data.local.Guest
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.data.network.BreezeChmsApiService
import com.writestreams.checkin.data.network.MailgunService
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.util.ApiKeys
import com.writestreams.checkin.util.ApiKeys.MAILGUN_URL
import com.writestreams.checkin.util.ChildLabel
import com.writestreams.checkin.util.GuestLabel
import com.writestreams.checkin.util.ParentLabel
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
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class CheckinService(private val context: Context) {

    private val repository = Repository(context)
    private val apiService: BreezeChmsApiService
    private val bluetoothPrintService = BluetoothPrintService(context)
    private val mailgunService: MailgunService
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy (h:mm a)")

    companion object {
        const val FAMILY_ROLE_CHILD = "2"
        var checkinCounter = 0
    }

    init {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .client(client)
            .baseUrl("https://sgcwoodstock.breezechms.com/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(BreezeChmsApiService::class.java)

        val mailClient = OkHttpClient.Builder().build()
        val mailRetrofit = Retrofit.Builder()
            .baseUrl(MAILGUN_URL)
            .client(mailClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        mailgunService = mailRetrofit.create(MailgunService::class.java)
    }

    fun checkinFamily(allFamilyMembers: List<FamilyMember>, checkedFamilyMembers: Set<FamilyMember>) {
        val currentDateTime = LocalDateTime.now()
        val formattedDateTime = dateTimeFormatter.format(currentDateTime)
        val checkinCode = Random.nextInt(1000, 9999).toString()
        val breezeInstanceId = getBreezeInstanceId()
        CoroutineScope(Dispatchers.IO).launch {
            val parentFamilyMembers = allFamilyMembers.filter { it.family_role_id != FAMILY_ROLE_CHILD }
            val parentPersons = parentFamilyMembers.map { repository.getPersonById(it.person_id) }
            val childPersons = mutableListOf<Person>()
            for (member in checkedFamilyMembers) {
                val childPerson = repository.getPersonById(member.person_id)
                childPerson?.let {
                    it.checkinDateTime = currentDateTime
                    it.checkinCode = checkinCode
                    it.checkinCounter = (++checkinCounter).toString()
                    repository.updatePerson(it)
                    printChildLabel(it, parentPersons, formattedDateTime)
                    checkInWithBreeze(it, currentDateTime, breezeInstanceId)
                    childPersons.add(childPerson)
                }
            }
            printParentLabel(parentPersons, checkinCode, formattedDateTime)
        }
    }

    fun checkOutPerson(person: Person) {
        CoroutineScope(Dispatchers.IO).launch {
            person.checkinDateTime = null
            person.checkinCode = null
            person.checkinCounter = null
            repository.updatePerson(person)
            checkOutWithBreeze(person, getBreezeInstanceId())
        }
    }

    fun checkInGuest(guest: Guest) {
        Log.d("checkinGuest:", guest.toString())
        CoroutineScope(Dispatchers.IO).launch {
            guest.checkinDateTime = dateTimeFormatter.format(LocalDateTime.now())
            guest.checkinCode = Random.nextInt(1000, 9999).toString()
            guest.checkinCounter = (++checkinCounter).toString()
            printGuestLabels(guest)
            emailGuestInfo(guest)
        }
    }

    private suspend fun printChildLabel(child: Person, parentPersons: List<Person?>, formattedDateTime: String) {
        val (parentName, parent2Name, phoneNumber) = getParentInfo(parentPersons)
        val childName = "${child.first_name} ${child.last_name}"
        val childLabel = ChildLabel(formattedDateTime, child.checkinCounter!!,
            childName, phoneNumber, child.checkinCode!!, "$parentName - $parent2Name")
        bluetoothPrintService.printLabel(childLabel)
    }

    private suspend fun printParentLabel(parentPersons: List<Person?>, checkinCode: String, formattedDateTime: String) {
        val (parentName, parent2Name, _) = getParentInfo(parentPersons)
        val parentLabel = ParentLabel(formattedDateTime,
            parentName, parent2Name, checkinCode)
        bluetoothPrintService.printLabel(parentLabel)
    }
    private suspend fun printGuestLabels(guest: Guest) {
        val parentName = "${guest.firstName} ${guest.lastName}"
        val parentLabel = ParentLabel(guest.checkinDateTime,
            parentName, "", guest.checkinCode)
        val guestLabel = GuestLabel(guest.checkinDateTime,
            parentName, guest.phoneNumber, guest.emailAddress, guest.childNames)
        bluetoothPrintService.printLabel(guestLabel)    // For greeter to keep
        guest.childNames.forEach {
            val childLabel = ChildLabel(guest.checkinDateTime, (checkinCounter++).toString(),
                it, guest.phoneNumber, guest.checkinCode, parentName)
            bluetoothPrintService.printLabel(childLabel)
        }
        bluetoothPrintService.printLabel(parentLabel)
    }

    private suspend fun checkInWithBreeze(child: Person, currentDateTime: LocalDateTime, breezeInstanceId: String) {
        Log.d("checkinFamily", "Checked in child: ${child.first_name} ${child.last_name} at $currentDateTime")
        try {
            apiService.checkIn(child.id, breezeInstanceId)
        } catch (e: Exception) {
            Log.e("checkInWithBreeze",
                "Exception calling checkIn API for ${child.first_name} ${child.last_name}", e)
        }
    }

    private suspend fun checkOutWithBreeze(person: Person, breezeInstanceId: String) {
        Log.d("checkOutWithBreeze", "Checked out ${person.first_name} ${person.last_name}")
        apiService.checkIn(person.id, breezeInstanceId, "out")
    }
    
    private fun getParentInfo(parentPersons: List<Person?>): Triple<String, String, String> {
        val parentInfo = Triple(
            parentPersons.getOrNull(0)?.let { "${it.first_name} ${it.last_name}" } ?: "",
            parentPersons.getOrNull(1)?.let { "${it.first_name} ${it.last_name}" } ?: "",
            parentPersons.getOrNull(0)?.details?.phoneDetails?.firstOrNull
                { it.phone_number.isNotEmpty() }?.phone_number ?: ""
        )
        return parentInfo
    }

    private fun getBreezeInstanceId(): String {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("breeze_instance_id", "210398284") ?: "210398284"
    }

    private fun emailGuestInfo(guest: Guest) {
        val parentName = "${guest.firstName} ${guest.lastName}"
        val date = DateTimeFormatter.ofPattern("MMM d, yyyy").format(LocalDateTime.now())
        val childNames = guest.childNames.joinToString(separator = "\n")
        val body = "$parentName\n${guest.phoneNumber}\n${guest.emailAddress}\n\nChildren:\n$childNames"
        val credentials = Base64.getEncoder().encodeToString(ApiKeys.MAILGUN_API_KEY.toByteArray())
        val authorization = "Basic $credentials"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = mailgunService.sendEmail(
                    authorization,
                    "Express Check-in <cmcheckin@sgcatlanta.org>",
                    ApiKeys.EMAIL_RECIPIENTS,
                    "SGC Children's Ministry - Guest - $parentName - $date",
                    body,
                    body
                ).execute()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Emailed the guest info", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to email the guest info", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error emailing the guest info: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("AttendanceService.emailAttendanceList exception", e.message, e)
            }
        }
    }
}