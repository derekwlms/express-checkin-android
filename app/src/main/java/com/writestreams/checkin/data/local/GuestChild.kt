package com.writestreams.checkin.data.local

import java.time.LocalDate

data class GuestChild(
    val firstName: String,
    val lastName: String,
    var dateOfBirth: LocalDate?,
    var specialNeeds: String = ""
) {
    fun fullName(): String {
        return "$firstName $lastName"
    }
}