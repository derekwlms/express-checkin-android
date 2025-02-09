package com.writestreams.checkin.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime

@Entity(tableName = "persons")
@TypeConverters(Converters::class)
data class Person(
    @PrimaryKey val id: String,
    val first_name: String,
    val force_first_name: String,
    val last_name: String,
    val nick_name: String,
    val middle_name: String,
    val maiden_name: String,
    val path: String,
    val details: PersonDetails,
    val family: List<FamilyMember>,
    var checkinDateTime: LocalDateTime?,
    var checkinCode: String? = "",
    var checkinCounter: String? = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Person

        if (id != other.id) return false
        if (first_name != other.first_name) return false
        if (last_name != other.last_name) return false
        if (family != other.family) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + first_name.hashCode()
        result = 31 * result + last_name.hashCode()
        result = 31 * result + family.hashCode()
        return result
    }
}

data class PersonDetails(
    val person_id: String,
    @SerializedName("194881525") val phoneDetails: List<PhoneDetail>,
    @SerializedName("951543614") val emailDetails: List<EmailDetail>
)

data class PhoneDetail(
    val field_type: String,
    val phone_number: String,
    val phone_type: String?,
    val do_not_text: String?,
    val is_private: String?,
    val people_meta_id: String,
    val sms_enrollment_status: String,
    val sms_enrollment_updated_on: String
)

data class EmailDetail(
    val address: String,
    val is_primary: String,
    val allow_bulk: String,
    val is_private: String,
    val field_type: String
)

data class FamilyMember(
    val id: String,
    val oid: String,
    val person_id: String,
    val family_id: String,
    val family_role_id: String,
    val created_on: String,
    val role_name: String,
    val role_id: String,
    val order: String,
    val details: FamilyMemberDetails,
    var checkinDateTime: LocalDateTime?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FamilyMember

        if (id != other.id) return false
        if (person_id != other.person_id) return false
        if (details != other.details) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + person_id.hashCode()
        result = 31 * result + details.hashCode()
        return result
    }
}

data class FamilyMemberDetails(
    val id: String,
    val first_name: String,
    val force_first_name: String,
    val last_name: String,
    val thumb_path: String,
    val path: String
)