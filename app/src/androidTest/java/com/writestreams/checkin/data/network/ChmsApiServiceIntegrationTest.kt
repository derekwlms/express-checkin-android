package com.writestreams.checkin.data.network

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.writestreams.checkin.data.local.AppDatabase
import com.writestreams.checkin.data.local.PersonDao
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BreezeChmsApiServiceIntegrationTest {

    private lateinit var apiService: BreezeChmsApiService
    private lateinit var db: AppDatabase
    private lateinit var personDao: PersonDao

    @Before
    fun setUp() {
        // Set up in-memory database
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        personDao = db.personDao()

        // Set up Retrofit
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://sgcwoodstock.breezechms.com/api/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(BreezeChmsApiService::class.java)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testGetPersons() {
        val response = apiService.getPersons().execute()
        assertTrue(response.isSuccessful)
        val persons = response.body()
        assertTrue(persons != null && persons.isNotEmpty())

        // Insert persons into the database
        persons?.let { personDao.insertAll(it) }

        // Verify the persons are inserted
        val cachedPersons = personDao.getAllPersons()
        assertTrue(cachedPersons.isNotEmpty())
    }
}