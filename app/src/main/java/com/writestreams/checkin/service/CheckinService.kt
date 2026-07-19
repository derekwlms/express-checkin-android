package com.writestreams.checkin.service

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import com.writestreams.checkin.data.local.Checkin
import com.writestreams.checkin.data.local.FamilyMember
import com.writestreams.checkin.data.local.Guest
import com.writestreams.checkin.data.local.GuestChild
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.data.network.BreezeChmsApiService
import com.writestreams.checkin.data.network.MailgunService
import com.writestreams.checkin.data.network.NetworkClients
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.util.ApiKeys
import com.writestreams.checkin.util.ChildLabel
import com.writestreams.checkin.util.GuestLabel
import com.writestreams.checkin.util.ParentLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlin.random.Random

class CheckinService(private val context: Context) {

    private val repository = Repository(context)
    private val apiService: BreezeChmsApiService = NetworkClients.breezeApiService
    private val bluetoothPrintService = BluetoothPrintService(context)
    private val mailgunService: MailgunService = NetworkClients.mailgunService
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy (h:mm a)")
        // Do not print Breeze-automated notes on labels; exampLe:  "3042 - Fred & Wilma"
    private val EXCLUDED_NOTES_FORMAT_REGEX = """^\d{2,}\s*-\s*.+""".toRegex()

    companion object {
        const val FAMILY_ROLE_CHILD = "2"
        const val FAMILY_ROLE_HEAD_OF_HOUSEHOLD = "4"
        const val OFFLINE_BREEZE_ID_PREFIX = "OL_"
    }

    fun checkinFamily(
        existingFamilyMembers: List<FamilyMember>,
        checkedFamilyMembers: Set<FamilyMember>,
        newChildren: List<GuestChild>,
        mobilePhoneNumber: String
    ) {
        val currentDateTime = LocalDateTime.now()
        val formattedDateTime = dateTimeFormatter.format(currentDateTime)
        val checkinCode = Random.nextInt(1000, 9999).toString()
        val breezeInstanceId = getBreezeInstanceId()
        CoroutineScope(Dispatchers.IO).launch {
            val parentFamilyMembers =
                existingFamilyMembers.filter { it.family_role_id != FAMILY_ROLE_CHILD }
            val parentPersons = parentFamilyMembers.map { repository.getPersonById(it.person_id) }
            for (member in checkedFamilyMembers) {
                val childPerson = repository.getPersonById(member.person_id)
                childPerson?.let {
                    val counter = repository.nextCheckinCounter()
                    repository.checkIn(Checkin(it.id, breezeInstanceId, currentDateTime, checkinCode, counter))
                    printChildLabel(it, parentPersons, formattedDateTime, mobilePhoneNumber, checkinCode, counter)
                    if (checkInWithBreeze(it.id, breezeInstanceId)) {
                        repository.setCheckedInWithBreeze(it.id, breezeInstanceId, LocalDateTime.now())
                    }
                }
            }
            for (guestChild in newChildren) {
                val guest = guestChild.asGuest()
                guest.let {
                    it.checkinDateTime = formattedDateTime
                    it.checkinCode = checkinCode
                    it.checkinCounter = repository.nextCheckinCounter()
                    printGuestChildLabel(it, parentPersons, formattedDateTime)
                    val primaryParent = parentPersons.firstOrNull()
                    if (primaryParent != null) {
                        addNewChildToBreeze(it, primaryParent)
                    }
                    emailGuestInfo(guest, primaryParent)
                    addNewChildToRepository(it, primaryParent)
                    recordLocalCheckin(it.breezeId, breezeInstanceId, currentDateTime, checkinCode, it.checkinCounter)
                }
            }
            printParentLabel(parentPersons, checkinCode, formattedDateTime)
        }
    }

    fun checkOutPerson(person: Person) {
        CoroutineScope(Dispatchers.IO).launch {
            repository.checkOut(person.id)
            checkOutWithBreeze(person, getBreezeInstanceId())
        }
    }

    fun checkInGuest(guest: Guest) {
        Log.d("checkinGuest:", guest.toString())
        CoroutineScope(Dispatchers.IO).launch {
            val currentDateTime = LocalDateTime.now()
            val breezeInstanceId = getBreezeInstanceId()
            guest.checkinDateTime = dateTimeFormatter.format(currentDateTime)
            guest.checkinCode = Random.nextInt(1000, 9999).toString()
            printGuestLabels(guest)     // assigns each child's checkinCounter
            emailGuestInfo(guest)
            addGuestToBreeze(guest)
            addGuestToRepository(guest)
            for (guestChild in guest.children) {
                recordLocalCheckin(guestChild.breezeId, breezeInstanceId, currentDateTime,
                    guest.checkinCode, guestChild.checkinCounter)
            }
        }
    }

    // Writes the local check-in row for a person whose id is now final (either a
    // Breeze id or an assigned OL_ offline id), marking it synced only after a
    // confirmed Breeze check-in.
    private suspend fun recordLocalCheckin(personId: String?, instanceId: String,
                                           checkinDateTime: LocalDateTime,
                                           checkinCode: String?, checkinCounter: String?) {
        if (personId.isNullOrEmpty()) {
            Log.w("recordLocalCheckin", "No person id assigned; skipping local check-in row")
            return
        }
        val synced = !personId.startsWith(OFFLINE_BREEZE_ID_PREFIX) && checkInWithBreeze(personId, instanceId)
        repository.checkIn(Checkin(personId, instanceId, checkinDateTime, checkinCode, checkinCounter,
            if (synced) LocalDateTime.now() else null))
    }

    fun sendLocalDataToBreeze() {
        CoroutineScope(Dispatchers.IO).launch {
            var message = ""
            if (isBreezeAccessible()) {
                message += sendPendingNewPersonsToBreeze()
                message += sendPendingCheckinsToBreeze()
            } else {
                message = "Unable to access Breeze; cannot send pending data now"
            }
            withContext(Dispatchers.Main) {
                if (message.isEmpty()) {
                    message = "Successfully sent cached data to Breeze"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun sendPendingCheckinsToBreeze(): String {
        return try {
            // Pending rows from past instances are included: each is pushed to its
            // own stored instance id, so late syncs land on the correct event.
            val pendingCheckins = repository.getAllPendingCheckins()
                .filterNot { it.personId.startsWith(OFFLINE_BREEZE_ID_PREFIX) }
            var sentCount = 0
            pendingCheckins.forEach { checkin ->
                if (checkInWithBreeze(checkin.personId, checkin.instanceId)) {
                    repository.setCheckedInWithBreeze(checkin.personId, checkin.instanceId, LocalDateTime.now())
                    sentCount++
                }
            }
            "$sentCount of ${pendingCheckins.count()} check-ins"
        } catch (e: Exception) {
            Log.e("CheckinService.sendLocalDataToBreeze",
                    "Exception sending checked-in persons, probably offline", e)
            "Unable to send check-ins, probably offline"
        }
    }

    suspend fun sendPendingNewPersonsToBreeze(): String {
        return try {
            val pendingNewPersons = repository.getPendingNewPersons()
            var sentCount = 0
            pendingNewPersons.forEach { person ->
                val offlineId = person.id
                val guest = person.asGuest()
                Log.i("sendPendingNewPersonsToBreeze", "adding $person, $guest")
                addGuestToBreeze(guest)
                val breezeId = guest.breezeId
                // Only replace the local OL_ person once Breeze has assigned a real id;
                // otherwise keep the offline person queued for the next sync.
                if (breezeId.isNotEmpty() && !breezeId.startsWith(OFFLINE_BREEZE_ID_PREFIX)) {
                    repository.deletePerson(person)
                    addGuestToRepository(guest)
                    repository.remapPersonCheckins(offlineId, breezeId)
                    sentCount++
                }
            }
            "$sentCount of ${pendingNewPersons.count()} additions, "
        } catch (e: Exception) {
            Log.e("CheckinService.sendPendingNewPersonsToBreeze",
                    "Exception sending pending new persons, probably offline", e)
            "Unable to send additions, probably offline"
        }
    }

    // Weekly rollover, run at launch: push anything still pending (to its stored
    // instance), then purge synced rows from past instances. Pending rows that
    // could not be pushed are kept for a later attempt.
    suspend fun rolloverCheckins(currentInstanceId: String) {
        if (isBreezeAccessible()) {
            sendPendingNewPersonsToBreeze()
            sendPendingCheckinsToBreeze()
        }
        repository.purgeSyncedCheckinsFromOtherInstances(currentInstanceId)
    }

    suspend fun getPhoneNumbers(person: Person): List<String> {
        val phoneNumbers = mutableListOf<String>()
        person.family.forEach { familyMember ->
            val familyMemberPerson = repository.getPersonById(familyMember.person_id)
            familyMemberPerson?.details?.phoneDetails?.forEach { phoneDetail ->
                phoneDetail.phone_number.takeIf { it.isNotEmpty() }?.let { phoneNumbers.add(it) }
            }
        }
        return phoneNumbers.distinct()
    }

    private suspend fun printChildLabel(child: Person, parentPersons: List<Person?>,
                                        formattedDateTime: String, mobilePhoneNumber: String,
                                        checkinCode: String, checkinCounter: String) {
        val (parentName, parent2Name, parentPhoneNumber) = getParentInfo(parentPersons)
        val childName = "${child.first_name} ${child.last_name}"
        val childPhoneNumber = child.details?.phoneDetails?.firstOrNull {
            it.phone_number.isNotEmpty() }?.phone_number.orEmpty()
        val childNotes = child.details?.notes?.let {
            if (EXCLUDED_NOTES_FORMAT_REGEX.matches(it)) ""
            else it.take(it.indexOf('.').takeIf { it != -1 } ?: 30) } ?: ""
        val phoneNumber = mobilePhoneNumber.ifEmpty { childPhoneNumber.ifEmpty { parentPhoneNumber }}
        val childLabel = ChildLabel(formattedDateTime, checkinCounter,
            childName, phoneNumber, checkinCode, "$parentName - $parent2Name", childNotes)
        bluetoothPrintService.printLabel(childLabel)
    }

    private suspend fun printGuestChildLabel(guest: Guest, parentPersons: List<Person?>, formattedDateTime: String) {
        val (parentName, parent2Name, phoneNumber) = getParentInfo(parentPersons)
        val childName = "${guest.firstName} ${guest.lastName}"
        val childLabel = ChildLabel(formattedDateTime, guest.checkinCounter,
            childName, phoneNumber, guest.checkinCode, "$parentName - $parent2Name", "")
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
            it.checkinCounter = repository.nextCheckinCounter()
            val childLabel = ChildLabel(guest.checkinDateTime, it.checkinCounter,
                it.fullName(), guest.phoneNumber, guest.checkinCode, parentName, "")
            bluetoothPrintService.printLabel(childLabel)
        }
        bluetoothPrintService.printLabel(parentLabel)
    }

    // Pure Breeze call; the local checkins table is updated by callers based on
    // the returned success so a failed API call is never marked as synced.
    private suspend fun checkInWithBreeze(personId: String, breezeInstanceId: String): Boolean {
        return try {
            val response = apiService.checkIn(personId, breezeInstanceId)
            if (!response.isSuccessful) {
                Log.e("checkInWithBreeze",
                    "checkIn API returned ${response.code()} for person id $personId")
            }
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("checkInWithBreeze",
                "Exception calling checkIn API for person id $personId", e)
            false
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
        return SettingsService.currentBreezeInstanceId(context)
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
        if (!parentBreezeId.isNullOrEmpty()) {
            try {
                val fieldsList = getBreezeFamilyRoleFields(parentBreezeId, FAMILY_ROLE_HEAD_OF_HOUSEHOLD)
                apiService.updatePerson(parentBreezeId, Gson().toJson(fieldsList))
            } catch (e: Exception) {
                Log.e("addGuestToBreeze", "Exception calling updatePerson for the parent - family_role", e)
            }
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
            if (breezeId != null) {
                try {
                    val fieldsList = getBreezeFamilyRoleFields(breezeId, FAMILY_ROLE_CHILD)
                    apiService.updatePerson(breezeId, Gson().toJson(fieldsList))
                } catch (e: Exception) {
                    Log.e("addGuestToBreeze", "Exception calling updatePerson for the child ($breezeId) - family_role", e)
                }
                checkInWithBreeze(breezeId, getBreezeInstanceId())
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
            checkInWithBreeze(breezeId, getBreezeInstanceId())
        }
    }

    suspend fun isBreezeAccessible() : Boolean {
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
                            checkInWithBreeze(guestChild.breezeId, getBreezeInstanceId())
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