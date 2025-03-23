package com.writestreams.checkin.service

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import com.writestreams.checkin.data.local.FamilyMember
import com.writestreams.checkin.data.local.Guest
import com.writestreams.checkin.data.local.GuestChild
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

    fun checkinFamily(existingFamilyMembers: List<FamilyMember>,
                      checkedFamilyMembers: Set<FamilyMember>,
                      newChildren: List<GuestChild>) {
        val currentDateTime = LocalDateTime.now()
        val formattedDateTime = dateTimeFormatter.format(currentDateTime)
        val checkinCode = Random.nextInt(1000, 9999).toString()
        val breezeInstanceId = getBreezeInstanceId()
        CoroutineScope(Dispatchers.IO).launch {
            val parentFamilyMembers = existingFamilyMembers.filter { it.family_role_id != FAMILY_ROLE_CHILD }
            val parentPersons = parentFamilyMembers.map { repository.getPersonById(it.person_id) }
            for (member in checkedFamilyMembers) {
                val childPerson = repository.getPersonById(member.person_id)
                childPerson?.let {
                    it.checkinDateTime = currentDateTime
                    it.checkinCode = checkinCode
                    it.checkinCounter = (++checkinCounter).toString()
                    repository.updatePerson(it)
                    printChildLabel(it, parentPersons, formattedDateTime)
                    checkInWithBreeze(it.id, currentDateTime, breezeInstanceId)
                }
            }
            for (guestChild in newChildren) {
                val guest = guestChild.asGuest()
                guest.let {
                    it.checkinDateTime = formattedDateTime
                    it.checkinCode = checkinCode
                    it.checkinCounter = (++checkinCounter).toString()
                    printGuestChildLabel(it, parentPersons, formattedDateTime)
                    addNewChildToBreeze(it, parentPersons.first()!!)
                    emailGuestInfo(guest, parentPersons.firstOrNull())
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
            addGuestToBreeze(guest)
        }
    }

    private suspend fun printChildLabel(child: Person, parentPersons: List<Person?>, formattedDateTime: String) {
        val (parentName, parent2Name, phoneNumber) = getParentInfo(parentPersons)
        val childName = "${child.first_name} ${child.last_name}"
        val childLabel = ChildLabel(formattedDateTime, child.checkinCounter!!,
            childName, phoneNumber, child.checkinCode!!, "$parentName - $parent2Name")
        bluetoothPrintService.printLabel(childLabel)
    }

    private suspend fun printGuestChildLabel(guest: Guest, parentPersons: List<Person?>, formattedDateTime: String) {
        val (parentName, parent2Name, phoneNumber) = getParentInfo(parentPersons)
        val childName = "${guest.firstName} ${guest.lastName}"
        val childLabel = ChildLabel(formattedDateTime, guest.checkinCounter,
            childName, phoneNumber, guest.checkinCode, "$parentName - $parent2Name")
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
        val childNames = guest.children.map { it.fullName() }
        val guestLabel = GuestLabel(guest.checkinDateTime,
            parentName, guest.phoneNumber, guest.emailAddress, childNames)
        bluetoothPrintService.printLabel(guestLabel)    // For greeter to keep
        guest.children.forEach {
            val childLabel = ChildLabel(guest.checkinDateTime, (checkinCounter++).toString(),
                it.fullName(), guest.phoneNumber, guest.checkinCode, parentName)
            bluetoothPrintService.printLabel(childLabel)
        }
        bluetoothPrintService.printLabel(parentLabel)
    }

    private suspend fun checkInWithBreeze(childId: String, currentDateTime: LocalDateTime, breezeInstanceId: String) {
        Log.d("checkinFamily", "Checked in child: $childId at $currentDateTime")
        try {
            apiService.checkIn(childId, breezeInstanceId)
        } catch (e: Exception) {
            Log.e("checkInWithBreeze",
                "Exception calling checkIn API for child id $childId", e)
        }
    }

    private suspend fun checkOutWithBreeze(person: Person, breezeInstanceId: String) {
        Log.d("checkOutWithBreeze", "Checked out ${person.first_name} ${person.last_name}")
//        apiService.checkIn(person.id, breezeInstanceId, "out")
        apiService.deleteCheckin(person.id, breezeInstanceId)
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

    private fun emailGuestInfo(guest: Guest, parent: Person? = null) {
        // TODO Improve this: HTML email, better new child information
        val parentName = guest.fullName()
        val date = DateTimeFormatter.ofPattern("MMM d, yyyy").format(LocalDateTime.now())
        var body = "$parentName - ${guest.phoneNumber} - ${guest.emailAddress} ${guest.dateOfBirth}\n\nChildren:\n\n"
        if (parent != null) {
            body += "Existing parent: ${parent.fullName()} - ${parent.getPrimaryEmailAddress()} - ${parent.getPrimaryPhone()}\n\n"
        }
        for (child in guest.children) {
            body += "\t${child.fullName()} - ${child.dateOfBirth} - ${child.specialNeeds}\n"
        }
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
                    body.replace("\n", "<br />")
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

    private suspend fun addGuestToBreeze(guest: Guest) {
        var familyMemberIds = emptyList<String>()
        try {
            val fieldsList = listOf(
                mapOf("field_id" to "300984657", "field_type" to "birthdate", "response" to guest.dateOfBirthMDY()),
                mapOf("field_id" to "194881525", "field_type" to "phone", "response" to true, "details" to mapOf("phone_mobile" to guest.phoneNumber)),
                mapOf("field_id" to "951543614", "field_type" to "email", "response" to true, "details" to mapOf("address" to guest.emailAddress))
            )
            Log.d("addGuestToBreeze - add new parent:", guest.toString())
            val responseJson = apiService.addPerson(guest.firstName, guest.lastName, Gson().toJson(fieldsList))
            Log.d("addGuestToBreeze - add new parent - apiService.addPerson response:", responseJson.toString())
            val breezeId = responseJson.body()?.get("id")?.asString
            if (!breezeId.isNullOrEmpty()) {
                familyMemberIds = familyMemberIds + breezeId
            }
        } catch (e: Exception) {
            Log.e("addGuestToBreeze", "Exception calling addPerson for the parent", e)
        }
        for (guestChild in guest.children) {
            Log.d("addGuestToBreeze - add child:", guestChild.toString())
            val fieldsList = listOf(
                mapOf("field_id" to "300984657", "field_type" to "birthdate", "response" to guestChild.dateOfBirthMDY()),
                mapOf("field_id" to "194881525", "field_type" to "phone", "response" to true, "details" to mapOf("phone_mobile" to guest.phoneNumber)),
                mapOf("field_id" to "951543614", "field_type" to "email", "response" to true, "details" to mapOf("address" to guest.emailAddress))
            )
            var breezeId: String? = null
            try {
                val responseJson = apiService.addPerson(guestChild.firstName, guestChild.lastName, Gson().toJson(fieldsList))
                Log.d("addGuestToBreeze - guestChild - apiService.addPerson response:", responseJson.toString())
                breezeId = responseJson.body()?.get("id")?.asString
                if (!breezeId.isNullOrEmpty()) {
                    familyMemberIds = familyMemberIds + breezeId
                }
            } catch (e: Exception) {
                Log.e("addGuestToBreeze - guestChild - apiService.addPerson",
                    "Exception calling add person for ${guestChild.firstName} ${guestChild.lastName}", e)
            }
            try {
                apiService.createFamily(Gson().toJson(familyMemberIds))
            } catch (e: Exception) {
                Log.e("addGuestToBreeze - addFamily",
                    "Exception calling add family for ${familyMemberIds}", e)
            }
            if (breezeId != null) {
                checkInWithBreeze(breezeId, LocalDateTime.now(), getBreezeInstanceId())
            }
        }
    }

    private suspend fun addNewChildToBreeze(guest: Guest, parent: Person) {
        val fieldsList = listOf(
            mapOf("field_id" to "300984657", "field_type" to "birthdate", "response" to guest.dateOfBirthMDY()),
            mapOf("field_id" to "194881525", "field_type" to "phone", "response" to true, "details" to mapOf("phone_mobile" to parent.getPrimaryPhone())),
            mapOf("field_id" to "951543614", "field_type" to "email", "response" to true, "details" to mapOf("address" to parent.getPrimaryEmailAddress()))
        )
        var breezeId: String? = null
        try {
            val responseJson = apiService.addPerson(guest.firstName, guest.lastName, Gson().toJson(fieldsList))
            Log.d("addNewChildToBreeze - apiService.addPerson response:", responseJson.toString())
            breezeId = responseJson.body()?.get("id")?.asString
        } catch (e: Exception) {
            Log.e("addNewChildToBreeze - apiService.addPerson",
                "Exception calling add person for ${guest.firstName} ${guest.lastName}", e)
        }
        if (breezeId != null) {
            try {
                apiService.addToFamily(Gson().toJson(listOf(breezeId)), parent.id)
            } catch (e: Exception) {
                Log.e("addNewChildToBreeze - addToFamily",
                    "Exception calling breeze to add $breezeId to family of ${parent.id}", e)
            }
            checkInWithBreeze(breezeId, LocalDateTime.now(), getBreezeInstanceId())
        }
    }
}