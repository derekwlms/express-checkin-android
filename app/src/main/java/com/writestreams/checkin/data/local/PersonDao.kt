package com.writestreams.checkin.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons")
    fun getAllPersons(): List<Person>

    @Query("SELECT * FROM persons WHERE id = :id")
    fun getPersonById(id: String): Person?

    @Query("SELECT * FROM persons WHERE id LIKE 'OL_%'")   // OFFLINE_BREEZE_ID_PREFIX = "OL_"
    fun getPendingNewPersons(): List<Person>

    @Query("SELECT COUNT(*) FROM persons WHERE id LIKE 'OL_%'")
    fun countPendingNewPersons(): Int

    @Query("SELECT id FROM persons")
    fun getAllPersonIds(): List<String>

    @Query("SELECT * FROM persons WHERE first_name LIKE :query OR last_name LIKE :query ORDER BY last_name, first_name")
    fun searchPersons(query: String): List<Person>

    @Upsert
    fun insertAll(persons: List<Person>)

    @Query("DELETE FROM persons")
    fun deleteAll()

    @Query("DELETE FROM persons WHERE id NOT LIKE 'OL_%'")
    fun deleteAllSynced()

    // Use @Transaction for an atomic fetch-then-swap
    // Retains local persons created while offline (OL_ IDs)
    @Transaction
    fun replaceAllFromBreeze(persons: List<Person>) {
        deleteAllSynced()
        insertAll(persons)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(person: Person)

    @Update
    fun update(person: Person)

    @Delete
    fun delete(person: Person)
}