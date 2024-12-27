package com.writestreams.checkin.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons")
    fun getAllPersons(): List<Person>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(persons: List<Person>)
}