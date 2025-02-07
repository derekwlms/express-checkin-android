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

    fun checkinFamily(person: Person, checkedFamilyMembers: Set<FamilyMember>, deviceAddress: String, labelText: String) {
        val currentDateTime = LocalDateTime.now()
        for (member in checkedFamilyMembers) {
            member.checkinDateTime = currentDateTime
            Log.d("checkinFamily", "Checked in family member: ${member.details.first_name} ${member.details.last_name} at $currentDateTime")
        }
        person.checkinDateTime = currentDateTime
        CoroutineScope(Dispatchers.IO).launch {
            // TODO: Also update Breeze
            // TODO: Print parent and child labels
            repository.updatePerson(person)
            bluetoothPrintService.printLabel(deviceAddress, labelText)
        }
    }
}