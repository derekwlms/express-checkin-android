package com.writestreams.checkin.data.local

import com.google.gson.annotations.SerializedName

data class Attendance(
    val instance_id: String,
    val person_id: String,
    val check_out: String,
    val created_on: String,
    @SerializedName("details") val attendee: Attendee
) {
    fun asPerson(): Person {
        return Person(
            id = attendee.id,
            first_name = attendee.first_name,
            last_name = attendee.last_name,
            force_first_name = attendee.force_first_name,
            nick_name = attendee.nick_name,
            middle_name = attendee.middle_name,
            maiden_name = "",
            details = PersonDetails(person_id, emptyList(), emptyList(), null, null, null),
            family = emptyList(),
            path = ""
        )
    }
}

data class Attendee(
    val id: String,
    val first_name: String,
    val force_first_name: String,
    val nick_name: String,
    val middle_name: String,
    val last_name: String,
)
