package com.writestreams.checkin.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromPersonDetails(details: PersonDetails): String {
        return Gson().toJson(details)
    }

    @TypeConverter
    fun toPersonDetails(detailsString: String): PersonDetails {
        val type = object : TypeToken<PersonDetails>() {}.type
        return Gson().fromJson(detailsString, type)
    }

    @TypeConverter
    fun fromFamilyMemberList(family: List<FamilyMember>): String {
        return Gson().toJson(family)
    }

    @TypeConverter
    fun toFamilyMemberList(familyString: String): List<FamilyMember> {
        val type = object : TypeToken<List<FamilyMember>>() {}.type
        return Gson().fromJson(familyString, type)
    }

    @TypeConverter
    fun fromPhoneDetailsMap(phoneDetails: Map<String, List<PhoneDetail>>): String {
        return Gson().toJson(phoneDetails)
    }

    @TypeConverter
    fun toPhoneDetailsMap(phoneDetailsString: String): Map<String, List<PhoneDetail>> {
        val type = object : TypeToken<Map<String, List<PhoneDetail>>>() {}.type
        return Gson().fromJson(phoneDetailsString, type)
    }
}