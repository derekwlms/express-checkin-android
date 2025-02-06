package com.writestreams.checkin.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons")
    fun getAllPersons(): List<Person>

    @Query("SELECT * FROM persons WHERE checkinDateTime IS NOT NULL")
    fun getCheckedInPersons(): List<Person>

    @Query("SELECT * FROM persons WHERE first_name LIKE :query OR last_name LIKE :query ORDER BY last_name, first_name")
    fun searchPersons(query: String): List<Person>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(persons: List<Person>)

    @Update
    fun update(person: Person)
}