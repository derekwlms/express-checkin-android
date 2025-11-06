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
    var checkinCounter: String? = "",
    var breezeSyncDateTime: LocalDateTime? = null,
    var tags: String? = ""
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

    fun fullName(): String {
        return "$first_name $last_name"
    }

    fun nameLastFirst(): String {
        return "$last_name, $first_name"
    }

    fun getPrimaryEmailAddress(): String {
        return details.emailDetails?.firstOrNull { it.address.isNotEmpty() }?.address ?: ""
    }

    fun getPrimaryPhone(): String {
        return details.phoneDetails?.firstOrNull { it.phone_number.isNotEmpty() }?.phone_number ?: ""
    }

    fun getFormattedCheckinTime(): String {
        return checkinDateTime?.let {
            checkinDateTime?.format(CHECKIN_TIME_FORMATTER) ?: ""
        } ?: ""
    }

    fun asGuest(): Guest {
        return Guest(
            firstName = this.first_name,
            lastName = this.last_name,
            dateOfBirth = null,
            phoneNumber = this.getPrimaryPhone(),
            emailAddress = this.getPrimaryEmailAddress(),
            breezeId = this.id,
            children = emptyList()
        )
    }

    companion object {
        private val CHECKIN_TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("M/d/yy h:mm:ss a")
    }
}

data class PersonDetails(
    val person_id: String,
    @SerializedName("194881525") val phoneDetails: List<PhoneDetail>,
    @SerializedName("951543614") val emailDetails: List<EmailDetail>,
    @SerializedName("300984657") val redundantBirthDate: String?,
    val birthdate: String?
)

data class PhoneDetail(
    val field_type: String,
    val phone_number: String,
    val phone_type: String?,
    val do_not_text: String?,
    val is_private: String?,
    val people_meta_id: String,
    val sms_enrollment_status: String,
    val sms_enrollment_updated_on: String) {

    constructor(phoneNumber: String) : this(
        field_type = "phone",
        phone_number = phoneNumber,
        phone_type = "mobile",
        do_not_text = "0",
        is_private = "0",
        people_meta_id = "",  // 1205817858
        sms_enrollment_status = "",
        sms_enrollment_updated_on = ""
    )
}

data class EmailDetail(
    val address: String,
    val is_primary: String,
    val allow_bulk: String,
    val is_private: String,
    val field_type: String
) {
    constructor(emailAddress: String) : this(
        field_type = "email_primary",
        address = emailAddress,
        is_primary = "1",
        allow_bulk = "1",
        is_private = "0"
    )
}

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
