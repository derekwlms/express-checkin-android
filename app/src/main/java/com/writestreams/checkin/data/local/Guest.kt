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
        val checkinLocalDateTime = runCatching {
            if (this.checkinDateTime.isNotEmpty())
                LocalDateTime.parse(this.checkinDateTime, DateTimeFormatter.ofPattern("MM/dd/yyyy"))
            else null }.getOrNull()
        return Person(
            id = this.breezeId,
            first_name = this.firstName,
            last_name = this.lastName,
            details = PersonDetails(
                person_id = this.breezeId,
                birthdate = this.dateOfBirthYMD(),
                redundantBirthDate = this.dateOfBirthYMD(),
                phoneDetails = listOf(PhoneDetail(this.phoneNumber)),
                emailDetails = listOf(EmailDetail(this.emailAddress))
            ),
            family = emptyList(), // this.children.map { it.asFamilyMember(this) },
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