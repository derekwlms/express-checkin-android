package com.writestreams.checkin.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface CheckinDao {
    // All rows, including ones checked out here but not yet confirmed by Breeze
    @Query("SELECT * FROM checkins WHERE instanceId = :instanceId")
    fun getForInstance(instanceId: String): List<Checkin>

    @Query("SELECT * FROM checkins WHERE instanceId = :instanceId AND checkoutDateTime IS NULL")
    fun getCheckedInForInstance(instanceId: String): List<Checkin>

    @Query("SELECT * FROM checkins WHERE instanceId = :instanceId AND checkoutDateTime IS NULL")
    fun observeCheckedInForInstance(instanceId: String): Flow<List<Checkin>>

    // Check-ins to push (any instance)
    @Query("SELECT * FROM checkins WHERE breezeSyncDateTime IS NULL AND checkoutDateTime IS NULL")
    fun getAllPending(): List<Checkin>

    // Check-outs to push (any instance)
    @Query("SELECT * FROM checkins WHERE checkoutDateTime IS NOT NULL")
    fun getPendingCheckouts(): List<Checkin>

    // Everything not yet in Breeze: unsent check-ins and check-outs
    @Query("SELECT * FROM checkins WHERE breezeSyncDateTime IS NULL OR checkoutDateTime IS NOT NULL")
    fun observeUnsent(): Flow<List<Checkin>>

    @Query("SELECT * FROM checkins WHERE breezeSyncDateTime IS NULL OR checkoutDateTime IS NOT NULL")
    fun getUnsent(): List<Checkin>

    @Query("UPDATE checkins SET checkoutDateTime = :checkoutDateTime WHERE personId = :personId AND instanceId = :instanceId")
    fun markCheckedOut(personId: String, instanceId: String, checkoutDateTime: LocalDateTime): Int

    @Query("SELECT * FROM checkins WHERE personId = :personId AND instanceId = :instanceId")
    fun get(personId: String, instanceId: String): Checkin?

    @Upsert
    fun upsert(checkin: Checkin)

    @Query("DELETE FROM checkins WHERE personId = :personId AND instanceId = :instanceId")
    fun delete(personId: String, instanceId: String)

    @Query("DELETE FROM checkins")
    fun deleteAll()

    // Weekly rollover: remove rows from past instances once synced to Breeze.
    // Unsynced (pending) rows and pending check-outs are kept so they can still be pushed later.
    @Query("DELETE FROM checkins WHERE instanceId != :instanceId AND breezeSyncDateTime IS NOT NULL AND checkoutDateTime IS NULL")
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
