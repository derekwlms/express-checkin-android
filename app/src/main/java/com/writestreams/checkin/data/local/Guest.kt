package com.writestreams.checkin.data.local

data class Guest(
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val emailAddress: String,
    val addToDirectory: Boolean,
    val childNames: List<String>,

    var checkinDateTime: String = "",
    var checkinCode: String = "",
    var checkinCounter: String = ""
)