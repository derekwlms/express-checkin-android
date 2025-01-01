package com.writestreams.checkin.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons")
    fun getAllPersons(): List<Person>

    @Query("SELECT * FROM persons WHERE first_name LIKE :query OR last_name LIKE :query")
    fun searchPersons(query: String): List<Person>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(persons: List<Person>)
}