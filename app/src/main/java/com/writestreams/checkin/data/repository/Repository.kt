package com.writestreams.checkin.data.repository

import android.content.Context
import androidx.room.Room
import com.writestreams.checkin.data.local.AppDatabase
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.data.local.PersonDao
import com.writestreams.checkin.data.network.BreezeChmsApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Repository(context: Context) {
    private val apiService: BreezeChmsApiService
    private val personDao: PersonDao

    init {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .client(client)
            .baseUrl("https://sgcwoodstock.breezechms.com/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(BreezeChmsApiService::class.java)

        val db = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java, "database-name"
        ).build()

        personDao = db.personDao()
    }

    suspend fun fetchAndCachePersons() {
        val persons = withContext(Dispatchers.IO) {
            suspendCancellableCoroutine<List<Person>> { continuation ->
                apiService.getPersons().enqueue(object : Callback<List<Person>> {
                    override fun onResponse(
                        call: Call<List<Person>>,
                        response: Response<List<Person>>
                    ) {
                        if (response.isSuccessful) {
                            response.body()?.let { continuation.resume(it) }
                                ?: continuation.resumeWithException(NullPointerException("Response body is null"))
                        } else {
                            continuation.resumeWithException(Exception("Response not successful"))
                        }
                    }

                    override fun onFailure(call: Call<List<Person>>, t: Throwable) {
                        continuation.resumeWithException(t)
                    }
                })
            }
        }

        withContext(Dispatchers.IO) {
            personDao.insertAll(persons)
        }
    }

    suspend fun resetAllCheckins() {
        return withContext(Dispatchers.IO) {
            personDao.resetAllCheckins()
        }
    }

    suspend fun getCachedPersons(): List<Person> {
        return withContext(Dispatchers.IO) {
            personDao.getAllPersons()
        }
    }

    suspend fun getPersonById(personId: String): Person? {
        return withContext(Dispatchers.IO) {
            personDao.getPersonById(personId)
        }
    }

    suspend fun getCheckedInPersons(): List<Person> {
        return withContext(Dispatchers.IO) {
            personDao.getCheckedInPersons()
        }
    }

    suspend fun searchPersons(query: String): List<Person> {
        return withContext(Dispatchers.IO) {
            personDao.searchPersons("%$query%")
        }
    }

    suspend fun updatePerson(person: Person) {
        withContext(Dispatchers.IO) {
            personDao.update(person)
        }
    }
}