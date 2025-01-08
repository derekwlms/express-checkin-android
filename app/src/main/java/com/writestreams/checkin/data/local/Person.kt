package com.writestreams.checkin.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

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
    val family: List<FamilyMember>
)

data class PersonDetails(
    val person_id: String,
    val phoneDetails: Map<String, List<PhoneDetail>>,
    val emailDetails: Map<String, List<EmailDetail>>
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
    val details: FamilyMemberDetails
)

data class FamilyMemberDetails(
    val id: String,
    val first_name: String,
    val force_first_name: String,
    val last_name: String,
    val thumb_path: String,
    val path: String
)