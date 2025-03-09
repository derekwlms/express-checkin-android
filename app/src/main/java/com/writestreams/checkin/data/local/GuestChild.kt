package com.writestreams.checkin.data.local

import java.time.LocalDateTime

data class GuestChild(
    val firstName: String,
    val lastName: String,
    var dateOfBirth: LocalDateTime?,
    var specialNeeds: String = ""
) {
    fun fullName(): String {
        return "$firstName $lastName"
    }
}