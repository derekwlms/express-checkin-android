package com.writestreams.checkin.data.local

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class GuestChild(
    val firstName: String,
    val lastName: String,
    var dateOfBirth: LocalDate?,
    var specialNeeds: String = "",
    var breezeId: String = ""
) {
    fun fullName(): String {
        return "$firstName $lastName"
    }
    fun dateOfBirthMDY(): String {
        return dateOfBirth?.format(DateTimeFormatter.ofPattern("M/d/yyyy")) ?: ""
    }

    fun asGuest(): Guest {
        return Guest(
            firstName = this.firstName,
            lastName = this.lastName,
            dateOfBirth = this.dateOfBirth,
            phoneNumber = "",
            emailAddress = "",
            breezeId = this.breezeId,
            children = emptyList()
        )
    }

    fun asPerson(): Person = asGuest().asPerson()
}