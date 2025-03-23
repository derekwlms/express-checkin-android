package com.writestreams.checkin.data.local

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Guest(
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val emailAddress: String,
    var dateOfBirth: LocalDate?,
    val children: List<GuestChild>,
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
}