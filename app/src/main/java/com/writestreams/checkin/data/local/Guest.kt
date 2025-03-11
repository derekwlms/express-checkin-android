package com.writestreams.checkin.data.local

import java.time.LocalDate

data class Guest(
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val emailAddress: String,
    var dateOfBirth: LocalDate?,
    val addToDirectory: Boolean,
    val children: List<GuestChild>,
    var checkinDateTime: String = "",
    var checkinCode: String = "",
    var checkinCounter: String = ""
) {
    fun fullName(): String {
        return "$firstName $lastName"
    }
}