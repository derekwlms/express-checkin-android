package com.writestreams.checkin.data.local

import java.time.LocalDateTime

data class Guest(
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val emailAddress: String,
    var dateOfBirth: LocalDateTime?,
    val addToDirectory: Boolean,
    val childNames: List<String>,
    var checkinDateTime: String = "",
    var checkinCode: String = "",
    var checkinCounter: String = ""
)