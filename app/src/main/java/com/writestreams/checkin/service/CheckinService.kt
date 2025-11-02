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
import com.writestreams.checkin.util.ApiKeys.BREEZE_API_URL
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
        const val FAMILY_ROLE_HEAD_OF_HOUSEHOLD = "4"
        const val OFFLINE_BREEZE_ID_PREFIX = "OL_"
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
            .baseUrl(BREEZE_API_URL)
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
                    addNewChildToRepository(it, parentPersons.firstOrNull())
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
            addGuestToRepository(guest)
        }
    }

    fun sendLocalDataToBreeze() {
        CoroutineScope(Dispatchers.IO).launch {
            var sentToBreeze = false
            var pendingPersons = emptyList<Person>()
            val isAvailable = isBreezeAccessible()
            if (isAvailable) {
                try {
                    pendingPersons = repository.getPendingCheckedInPersons()
                    val breezeInstanceId = getBreezeInstanceId()
                    // TODO Also add to breeze all guests and guestChilds that were created while offline
                    pendingPersons.forEach { person ->
                        checkInWithBreeze(person.id, LocalDateTime.now(), breezeInstanceId)
                    }
                    sentToBreeze = true
                } catch (e: Exception) {
                    Log.e(
                        "CheckinService.sendLocalDataToBreeze",
                        "Exception sending checked-in persons, probably offline", e
                    )
                }
            }
            withContext(Dispatchers.Main) {
                val message =
                    if (!isAvailable) {
                        "Unable to access Breeze; cannot send pending data now"
                    } else if (pendingPersons.isEmpty()) {
                        "No pending data to send to Breeze"
                    } else if (sentToBreeze) {
                        "Successfully sent cached data to Breeze"
                    } else {
                        "Unable to send cached data to Breeze"
                    }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun printChildLabel(child: Person, parentPersons: List<Person?>, formattedDateTime: String) {
        val (parentName, parent2Name, parentPhoneNumber) = getParentInfo(parentPersons)
        val childName = "${child.first_name} ${child.last_name}"
        val childPhoneNumber = child.details?.phoneDetails?.firstOrNull {
            it.phone_number.isNotEmpty() }?.phone_number.orEmpty()
        val phoneNumber = childPhoneNumber.ifEmpty { parentPhoneNumber }
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
            repository.setCheckedInWithBreeze(childId, currentDateTime)
        } catch (e: Exception) {
            Log.e("checkInWithBreeze",
                "Exception calling checkIn API for child id $childId", e)
        }
    }

    private suspend fun checkOutWithBreeze(person: Person, breezeInstanceId: String) {
        Log.d("checkOutWithBreeze", "Checking out ${person.first_name} ${person.last_name} for event $breezeInstanceId")
        try {
//        apiService.checkIn(person.id, breezeInstanceId, "out")
            apiService.deleteCheckin(person.id, breezeInstanceId)
        } catch (e: Exception) {
            Log.e("checkOutWithBreeze",
                "Exception calling deleteCheckin API for person id ${person.id}", e)
        }
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
        val date = DateTimeFormatter.ofPattern("MMM d, yyyy").format(LocalDateTime.now())
        val credentials = Base64.getEncoder().encodeToString(ApiKeys.MAILGUN_API_KEY.toByteArray())
        val authorization = "Basic $credentials"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = mailgunService.sendEmail(
                    authorization,
                    "Express Check-in <cmcheckin@sgcatlanta.org>",
                    ApiKeys.EMAIL_RECIPIENTS,
                    "SGC Children's Ministry - Added - ${guest.fullName()} - $date",
                    getNewGuestEmailBody(guest, parent),
                    getNewGuestEmailHtml(guest, parent)
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
        var parentBreezeId: String? = ""
        try {
            Log.d("addGuestToBreeze - add new parent:", guest.toString())
            val fieldsList = getBreezeFields(guest.dateOfBirthMDY(), guest.phoneNumber, guest.emailAddress)
            val responseJson = apiService.addPerson(guest.firstName, guest.lastName, Gson().toJson(fieldsList))
            Log.d("addGuestToBreeze - add new parent - apiService.addPerson response:", responseJson.toString())
            parentBreezeId = responseJson.body()?.get("id")?.asString
            if (!parentBreezeId.isNullOrEmpty()) {
                familyMemberIds = familyMemberIds + parentBreezeId
                guest.breezeId = parentBreezeId
            }
        } catch (e: Exception) {
            Log.e("addGuestToBreeze", "Exception calling addPerson for the parent", e)
        }
        try {
            val fieldsList = getBreezeFamilyRoleFields(parentBreezeId!!, FAMILY_ROLE_HEAD_OF_HOUSEHOLD)
            apiService.updatePerson(parentBreezeId, Gson().toJson(fieldsList))
        } catch (e: Exception) {
            Log.e("addGuestToBreeze", "Exception calling updatePerson for the parent - family_role", e)
        }
        for (guestChild in guest.children) {
            Log.d("addGuestToBreeze - add child:", guestChild.toString())
            var breezeId: String? = null
            try {
                val fieldsList = getBreezeFields(guestChild.dateOfBirthMDY(), guest.phoneNumber, guest.emailAddress)
                val responseJson = apiService.addPerson(guestChild.firstName, guestChild.lastName, Gson().toJson(fieldsList))
                Log.d("addGuestToBreeze - guestChild - apiService.addPerson response:", responseJson.toString())
                breezeId = responseJson.body()?.get("id")?.asString
                if (!breezeId.isNullOrEmpty()) {
                    familyMemberIds = familyMemberIds + breezeId
                    guestChild.breezeId = breezeId
                }
            } catch (e: Exception) {
                Log.e("addGuestToBreeze - guestChild - apiService.addPerson",
                    "Exception calling add person for ${guestChild.firstName} ${guestChild.lastName}", e)
            }
            try {
                val fieldsList = getBreezeFamilyRoleFields(breezeId!!, FAMILY_ROLE_CHILD)
                apiService.updatePerson(breezeId, Gson().toJson(fieldsList))
            } catch (e: Exception) {
                Log.e("addGuestToBreeze", "Exception calling updatePerson for the child ($breezeId) - family_role", e)
            }
            if (breezeId != null) {
                checkInWithBreeze(breezeId, LocalDateTime.now(), getBreezeInstanceId())
            }
        }
        try {
            apiService.createFamily(Gson().toJson(familyMemberIds))
        } catch (e: Exception) {
            Log.e("addGuestToBreeze - addFamily",
                "Exception calling add family for $familyMemberIds", e)
        }
        for ((index, familyMemberId) in familyMemberIds.withIndex()) {
            val familyRole = if (index == 0) FAMILY_ROLE_HEAD_OF_HOUSEHOLD else FAMILY_ROLE_CHILD
            val fieldsList = getBreezeFamilyRoleFields(familyMemberId, familyRole)
            try {
                apiService.updatePerson(familyMemberId, Gson().toJson(fieldsList))
            } catch (e: Exception) {
                Log.e("addGuestToBreeze", "Exception calling updatePerson for the family_role $familyRole, person_id $familyMemberId - family_role", e)
            }
        }
    }

    private suspend fun addNewChildToBreeze(guest: Guest, parent: Person) {
        var breezeId: String? = null
        try {
            val fieldsList = getBreezeFields(guest.dateOfBirthMDY(), parent.getPrimaryPhone(), parent.getPrimaryEmailAddress())
            val responseJson = apiService.addPerson(guest.firstName, guest.lastName, Gson().toJson(fieldsList))
            Log.d("addNewChildToBreeze - apiService.addPerson response:", responseJson.toString())
            breezeId = responseJson.body()?.get("id")?.asString
        } catch (e: Exception) {
            Log.e("addNewChildToBreeze - apiService.addPerson",
                "Exception calling add person for ${guest.firstName} ${guest.lastName}", e)
        }
        if (breezeId != null) {
            guest.breezeId = breezeId
            try {
                apiService.addToFamily(Gson().toJson(listOf(breezeId)), parent.id)
            } catch (e: Exception) {
                Log.e("addNewChildToBreeze - addToFamily",
                    "Exception calling breeze to add $breezeId to family of ${parent.id}", e)
            }
            try {
                val fieldsList = getBreezeFamilyRoleFields(breezeId, FAMILY_ROLE_CHILD)
                apiService.updatePerson(breezeId, Gson().toJson(fieldsList))
            } catch (e: Exception) {
                Log.e("addNewChildToBreeze", "Exception calling updatePerson for the child ($breezeId) - family_role", e)
            }
            checkInWithBreeze(breezeId, LocalDateTime.now(), getBreezeInstanceId())
        }
    }

    private suspend fun isBreezeAccessible() : Boolean {
        return try {
            apiService.getProfileFields()
            true
        } catch (e: Exception) {
            Log.e("isBreezeAccessible", "Exception calling getting profile fields, probably offline", e)
            false
        }
    }

    private suspend fun addGuestToRepository(guest: Guest) {
        if (guest.breezeId.isNullOrEmpty()) {
            guest.breezeId = OFFLINE_BREEZE_ID_PREFIX + System.currentTimeMillis()
            Log.d("addGuestToRepository",
                "Breeze ID not set, so assigning offline ID for guest: ${guest.breezeId}, guest: $guest")
            // If offline, fall back to using the guest data directly
            saveGuestToRepository(guest)
            return
        }
        try {
            // Fetch the newly created person from Breeze
            val breezeParentPerson = apiService.getPerson(guest.breezeId).execute().body()
            if (breezeParentPerson != null) {
                repository.addPerson(breezeParentPerson)
                for (guestChild in guest.children) {
                    if (!guestChild.breezeId.isNullOrEmpty()) {
                        val breezeChild = apiService.getPerson(guestChild.breezeId).execute().body()
                        if (breezeChild != null) {
                            repository.addPerson(breezeChild)
                            // Redundant check-in to work around Breeze delay on add
                            checkInWithBreeze(guestChild.breezeId, LocalDateTime.now(), getBreezeInstanceId())
                        }
                    }
                }
            } else {
                Log.w("addGuestToRepository", "Could not fetch person from Breeze, falling back to guest data")
                saveGuestToRepository(guest)
            }
        } catch (e: Exception) {
            Log.e("addGuestToRepository",
                "Exception fetching/adding guest family to repository - ${guest.firstName} ${guest.lastName}", e)
        }
    }

    private suspend fun saveGuestToRepository(guest: Guest) {
        val person = guest.asPerson()
        Log.d("saveGuestToRepository", "Adding to local repository: guest: $guest, person: $person")
        repository.addPerson(person)
        for (guestChild in guest.children) {
            if (guestChild.breezeId.isNullOrEmpty()) {
                guestChild.breezeId = "${OFFLINE_BREEZE_ID_PREFIX}${System.currentTimeMillis()}_${guestChild.firstName}"
            }
            repository.addPerson(guestChild.asPerson())
            // Redundant check-in to work around Breeze delay on add
            checkInWithBreeze(guestChild.breezeId, LocalDateTime.now(), getBreezeInstanceId())
        }
    }

    private suspend fun addNewChildToRepository(newChild: Guest, parent: Person?) {
        if (newChild.breezeId.isNullOrEmpty()) {
            newChild.breezeId = OFFLINE_BREEZE_ID_PREFIX + System.currentTimeMillis()
            Log.d("addNewChildToRepository",
                "Breeze ID not set, so assigning offline ID: ${newChild.breezeId}, guest: $newChild, parent: $parent")
            // If offline, fall back to using the guest data directly
            repository.addPerson(newChild.asPerson())
            return
        }
        try {
            // Fetch the newly created child from Breeze
            val newChildBreezePerson = apiService.getPerson(newChild.breezeId).execute().body()
            if (newChildBreezePerson != null) {
                Log.d("addNewChildToRepository",
                    "Adding Breeze person to repository: $newChildBreezePerson, parent: $parent")
                repository.addPerson(newChildBreezePerson)
            } else {
                Log.w("addNewChildToRepository",
                    "Could not fetch person from Breeze, falling back to guest data")
                repository.addPerson(newChild.asPerson())
            }
        } catch (e: Exception) {
            Log.e("addNewChildToRepository",
                "Exception fetching/adding child to repository - ${newChild.firstName} ${newChild.lastName}", e)
        }
    }

    private fun getBreezeFields(birthDate: String, phoneNumber: String, emailAddress: String): List<Map<String, Any>> {
        return listOf(
            mapOf("field_id" to "300984657", "field_type" to "birthdate", "response" to birthDate),
            mapOf("field_id" to "194881525", "field_type" to "phone", "response" to true, "details" to mapOf("phone_mobile" to phoneNumber)),
            mapOf("field_id" to "951543614", "field_type" to "email", "response" to true, "details" to mapOf("address" to emailAddress))
        )
    }

    private fun getBreezeFamilyRoleFields(breezeId: String, familyRole: String): List<Map<String, Any>> {
        return listOf(
            mapOf("field_id" to "62348923451", "field_type" to "family_role", "response" to familyRole, "details" to mapOf("person_id" to breezeId, "role_id" to familyRole))
        )
    }

    private fun getNewGuestEmailBody(guest: Guest, existingParent: Person?): String {
        var body = "${guest.fullName()} - ${guest.phoneNumber} - ${guest.emailAddress} ${guest.dateOfBirth}\n\n"
        if (existingParent != null) {
            body += "Existing parent: ${existingParent.fullName()} - ${existingParent.getPrimaryEmailAddress()} - ${existingParent.getPrimaryPhone()}\n\n"
        } else {
            body += "Children:\n\n"
            for (child in guest.children) {
                body += "\t${child.fullName()} - ${child.dateOfBirth} - ${child.specialNeeds}\n"
            }
        }
        return body
    }

    private fun getNewGuestEmailHtml(guest: Guest, existingParent: Person?): String {
        var html = """
            <h3>${if (existingParent == null) "New Family" else "New Child of Existing Family"}</h3>
            Name: &nbsp; ${guest.fullName()} <br/>
            Phone: &nbsp; ${guest.phoneNumber} <br/>
            Birth date: &nbsp; ${guest.dateOfBirth} <br/>
            Email: &nbsp; ${guest.emailAddress} <br/>
            Check-in: &nbsp; ${guest.checkinDateTime} <br/>
    """
        if (existingParent != null) {
            html += """
                <h3>Existing Parent:</h3>
                    Name: &nbsp; ${existingParent.fullName()} <br/>
                    Phone: &nbsp; ${existingParent.getPrimaryPhone()} <br/>
                    Email: &nbsp; ${existingParent.getPrimaryEmailAddress()} <br/>
            """
        } else {
            html += "<h3>Children:</h3><ul>"
            for (child in guest.children) {
                html += """
                    <li>Name: &nbsp; ${child.fullName()}  &emsp;
                        Birthdate: &nbsp; ${child.dateOfBirth} &emsp;
                        Special Needs: &nbsp; ${child.specialNeeds}
                    </li>
                """
            }
            html += "</ul>"
        }
        return html
    }
}