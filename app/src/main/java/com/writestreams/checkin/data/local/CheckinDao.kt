package com.writestreams.checkin.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import java.time.LocalDateTime

@Dao
interface CheckinDao {
    @Query("SELECT * FROM checkins WHERE instanceId = :instanceId")
    fun getForInstance(instanceId: String): List<Checkin>

    @Query("SELECT * FROM checkins WHERE instanceId = :instanceId AND breezeSyncDateTime IS NULL")
    fun getPendingForInstance(instanceId: String): List<Checkin>

    @Query("SELECT * FROM checkins WHERE breezeSyncDateTime IS NULL")
    fun getAllPending(): List<Checkin>

    @Query("SELECT COUNT(*) FROM checkins WHERE breezeSyncDateTime IS NULL")
    fun countPending(): Int

    @Query("SELECT * FROM checkins WHERE personId = :personId AND instanceId = :instanceId")
    fun get(personId: String, instanceId: String): Checkin?

    @Upsert
    fun upsert(checkin: Checkin)

    @Query("DELETE FROM checkins WHERE personId = :personId AND instanceId = :instanceId")
    fun delete(personId: String, instanceId: String)

    @Query("DELETE FROM checkins")
    fun deleteAll()

    // Weekly rollover: remove rows from past instances once synced to Breeze.
    // Unsynced (pending) rows are kept so they can still be pushed later.
    @Query("DELETE FROM checkins WHERE instanceId != :instanceId AND breezeSyncDateTime IS NOT NULL")
    fun purgeSyncedFromOtherInstances(instanceId: String)

    @Query("UPDATE checkins SET breezeSyncDateTime = :syncDateTime WHERE personId = :personId AND instanceId = :instanceId")
    fun setSynced(personId: String, instanceId: String, syncDateTime: LocalDateTime)

    // When an offline-created (OL_) person is later added to Breeze, their
    // check-in rows move to the Breeze-assigned id.
    @Query("UPDATE checkins SET personId = :newPersonId WHERE personId = :oldPersonId")
    fun remapPersonId(oldPersonId: String, newPersonId: String)

    @Query("SELECT MAX(CAST(checkinCounter AS INTEGER)) FROM checkins")
    fun getMaxCheckinCounter(): Int?
}
