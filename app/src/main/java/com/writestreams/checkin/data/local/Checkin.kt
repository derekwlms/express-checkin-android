package com.writestreams.checkin.data.local

import androidx.room.Entity
import androidx.room.TypeConverters
import java.time.LocalDateTime

/**
 * A check-in for one person at one event instance (one Sunday).
 * This table is the local check-in ledger: an offline outbox for pushes to Breeze
 * plus a fast local cache. It is not an archive - rows from past instances are
 * purged at launch once they have been synced to Breeze.
 */
@Entity(tableName = "checkins", primaryKeys = ["personId", "instanceId"])
@TypeConverters(Converters::class)
data class Checkin(
    val personId: String,
    val instanceId: String,
    val checkinDateTime: LocalDateTime,
    val checkinCode: String? = null,
    val checkinCounter: String? = null,
    var breezeSyncDateTime: LocalDateTime? = null
)
