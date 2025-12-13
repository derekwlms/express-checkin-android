package com.writestreams.checkin.data.local

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Guest(
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val emailAddress: String,
    var dateOfBirth: LocalDate?,
    val children: List<GuestChild>,
    var breezeId: String = "",
    var checkinDateTime: String = "",
    var checkinCode: String = "",
    var checkinCounter: String = ""
) {
    fun fullName(): String {
        return "$firstName $lastName"
    }
    fun dateOfBirthMDY(): String {
        return dateOfBirth?.format(DateTimeFormatter.ofPattern("M/d/yyyy")) ?: ""
    }
    fun dateOfBirthYMD(): String {
        return dateOfBirth?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: ""
    }

    fun asPerson(): Person {
        // Clean this up for consistent date/time formats
        // Some differences are necessary: breeze format, label format, and attendance list format
        val checkinLocalDateTime = runCatching {
            when {
                checkinDateTime.isEmpty() -> null
                else -> LocalDateTime.parse(checkinDateTime,
                    DateTimeFormatter.ofPattern("MMM d, yyyy (h:mm a)"))
            }
        }.recoverCatching {
            LocalDateTime.parse(checkinDateTime,
                DateTimeFormatter.ofPattern("MM/dd/yyyy"))
        }.getOrNull()
        return Person(
            id = this.breezeId,
            first_name = this.firstName,
            last_name = this.lastName,
            details = PersonDetails(
                person_id = this.breezeId,
                birthdate = this.dateOfBirthYMD(),
                redundantBirthDate = this.dateOfBirthYMD(),
                phoneDetails = listOf(PhoneDetail(this.phoneNumber)),
                emailDetails = listOf(EmailDetail(this.emailAddress)),
                notes = null
            ),
            family = emptyList(), // listOf(this.asFamilyMember()) + children.map { it.asFamilyMember() },
            checkinDateTime = checkinLocalDateTime,
            checkinCode = this.checkinCode,
            checkinCounter = this.checkinCounter,
            force_first_name = "",
            nick_name = "",
            middle_name = "",
            maiden_name = "",
            path = ""
        )
    }
}