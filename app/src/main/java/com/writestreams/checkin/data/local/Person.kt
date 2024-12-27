package com.writestreams.checkin.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class Person(
    @PrimaryKey val id: Int,
    val first_name: String,
    val force_first_name: String,
    val last_name: String,
    val path: String
)
