package com.writestreams.checkin.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.writestreams.checkin.service.SettingsService

@Database(entities = [Person::class, Checkin::class], version = 4)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun checkinDao(): CheckinDao

    companion object {
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE persons ADD COLUMN breezeSyncDateTime TEXT")
            }
        }

        // v4: check-in state moves out of persons into its own checkins table,
        // so refreshing the persons mirror from Breeze can never erase check-ins.
        // Existing check-in state is carried over, attributed to the current instance.
        private fun migration3To4(context: Context) = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS checkins (
                        personId TEXT NOT NULL,
                        instanceId TEXT NOT NULL,
                        checkinDateTime TEXT NOT NULL,
                        checkinCode TEXT,
                        checkinCounter TEXT,
                        breezeSyncDateTime TEXT,
                        PRIMARY KEY(personId, instanceId))"""
                )
                val instanceId = SettingsService.currentBreezeInstanceId(context)
                database.execSQL(
                    """INSERT OR REPLACE INTO checkins
                        (personId, instanceId, checkinDateTime, checkinCode, checkinCounter, breezeSyncDateTime)
                        SELECT id, ?, checkinDateTime, checkinCode, checkinCounter, breezeSyncDateTime
                        FROM persons WHERE checkinDateTime IS NOT NULL""",
                    arrayOf(instanceId)
                )
                database.execSQL(
                    """CREATE TABLE persons_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        first_name TEXT NOT NULL,
                        force_first_name TEXT NOT NULL,
                        last_name TEXT NOT NULL,
                        nick_name TEXT NOT NULL,
                        middle_name TEXT NOT NULL,
                        maiden_name TEXT NOT NULL,
                        path TEXT NOT NULL,
                        details TEXT NOT NULL,
                        family TEXT NOT NULL,
                        tags TEXT)"""
                )
                database.execSQL(
                    """INSERT INTO persons_new
                        SELECT id, first_name, force_first_name, last_name, nick_name,
                               middle_name, maiden_name, path, details, family, tags
                        FROM persons"""
                )
                database.execSQL("DROP TABLE persons")
                database.execSQL("ALTER TABLE persons_new RENAME TO persons")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addMigrations(MIGRATION_2_3, migration3To4(appContext))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
