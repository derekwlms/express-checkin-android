package com.writestreams.checkin.service

import android.content.Context
import android.util.Log
import com.writestreams.checkin.data.local.FamilyMember
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.util.ChildLabel
import com.writestreams.checkin.util.ParentLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class CheckinService(private val context: Context) {

    private val repository = Repository(context)
    private val bluetoothPrintService = BluetoothPrintService(context)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy (h:mm a)")

    companion object {
        const val FAMILY_ROLE_CHILD = "2"
        var checkinCounter = 0
    }

    fun checkinFamily(allFamilyMembers: List<FamilyMember>, checkedFamilyMembers: Set<FamilyMember>) {
        val currentDateTime = LocalDateTime.now()
        val formattedDateTime = dateTimeFormatter.format(currentDateTime)
        val checkinCode = Random.nextInt(1000, 9999).toString()
        CoroutineScope(Dispatchers.IO).launch {
            val parentFamilyMembers = allFamilyMembers.filter { it.family_role_id != FAMILY_ROLE_CHILD }
            val parentPersons = parentFamilyMembers.map { repository.getPersonById(it.person_id) }
            for ((index, member) in checkedFamilyMembers.withIndex()) {
                val familyMemberPerson = repository.getPersonById(member.person_id)
                familyMemberPerson?.let {
                    it.checkinDateTime = currentDateTime
                    it.checkinCode = checkinCode
                    it.checkinCounter = (++checkinCounter).toString()
                    repository.updatePerson(it)
                    printLabels(it, parentPersons, formattedDateTime, index == 0)
                    checkInWithBreeze(it, parentPersons, currentDateTime)
                }
            }
        }
    }

    private suspend fun printLabels(child: Person, parentPersons: List<Person?>, formattedDateTime: String, printParent: Boolean) {
        val parentName = "${parentPersons[0]?.first_name} ${parentPersons[0]?.last_name}"
        val parent2Name = "${parentPersons[1]?.first_name} ${parentPersons[1]?.last_name}"
        val phoneNumber = parentPersons[0]?.details?.phoneDetails?.firstOrNull {
            !it.phone_number.isNullOrEmpty()
        }?.phone_number ?: ""
        val parentLabel = ParentLabel(formattedDateTime,
            parentName, parent2Name, child.checkinCode!!,
            listOf("${child.first_name} ${child.last_name}"))
        if (printParent)
            bluetoothPrintService.printLabel(parentLabel)

        val childName = "${child.first_name} ${child.last_name}"
        val childLabel = ChildLabel(formattedDateTime, child.checkinCounter!!,
            childName, phoneNumber, child.checkinCode!!, "$parentName - $parent2Name")
        bluetoothPrintService.printLabel(childLabel)
    }

    private suspend fun checkInWithBreeze(child: Person, parentPersons: List<Person?>, currentDateTime: LocalDateTime) {
        Log.d("checkinFamily", "Checked in child: ${child.first_name} ${child.last_name} at $currentDateTime")
        // TODO Finish this once we find an API that works
    }
}