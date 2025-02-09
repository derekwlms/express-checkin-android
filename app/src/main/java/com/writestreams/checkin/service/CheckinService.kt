package com.writestreams.checkin.service

import android.content.Context
import android.util.Log
import com.writestreams.checkin.data.local.FamilyMember
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.data.repository.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class CheckinService(private val context: Context) {

    private val repository = Repository(context)
    private val bluetoothPrintService = BluetoothPrintService(context)

    companion object {
        const val FAMILY_ROLE_CHILD = "2"
    }

    fun checkinFamily(allFamilyMembers: List<FamilyMember>, checkedFamilyMembers: Set<FamilyMember>) {
        val currentDateTime = LocalDateTime.now()
        CoroutineScope(Dispatchers.IO).launch {
            val parentFamilyMembers = allFamilyMembers.filter { it.family_role_id != FAMILY_ROLE_CHILD }
            val parentPersons = parentFamilyMembers.map { repository.getPersonById(it.person_id) }
            printParentLabel(parentPersons, currentDateTime)
            for (member in checkedFamilyMembers) {
                val familyMemberPerson = repository.getPersonById(member.person_id)
                familyMemberPerson?.let {
                    it.checkinDateTime = currentDateTime
                    repository.updatePerson(it)
                    printChildLabel(it, currentDateTime)
                    checkInWithBreeze(it, parentPersons, currentDateTime)
                }
            }
        }
    }

    private suspend fun printParentLabel(parentPersons: List<Person?>, currentDateTime: LocalDateTime) {
        val labelText = parentPersons.joinToString(separator = "\n") { person ->
            "${person?.first_name} ${person?.last_name}\nChecked in at $currentDateTime"
        }
        bluetoothPrintService.printLabel(labelText)
    }

    private suspend fun printChildLabel(child: Person, currentDateTime: LocalDateTime) {
        val labelText = "${child.first_name} ${child.last_name}"
        bluetoothPrintService.printLabel(labelText)
    }

    private suspend fun checkInWithBreeze(child: Person, parentPersons: List<Person?>, currentDateTime: LocalDateTime) {
        Log.d("checkinFamily", "Checked in child: ${child.first_name} ${child.last_name} at $currentDateTime")
        // TODO Finish this once we find an API that works
    }
}