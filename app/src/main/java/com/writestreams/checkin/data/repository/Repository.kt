package com.writestreams.checkin.data.repository

import android.content.Context
import androidx.room.Room
import com.writestreams.checkin.data.local.AppDatabase
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.data.local.PersonDao
import com.writestreams.checkin.data.network.BreezeChmsApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class Repository(context: Context) {
    private val apiService: BreezeChmsApiService
    private val personDao: PersonDao

    init {
        val retrofit = Retrofit.Builder()
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

    fun fetchAndCachePersons() {
        apiService.getPersons().enqueue(object : Callback<List<Person>> {
            override fun onResponse(call: Call<List<Person>>, response: Response<List<Person>>) {
                if (response.isSuccessful) {
                    response.body()?.let { persons ->
                        personDao.insertAll(persons)
                    }
                }
            }

            override fun onFailure(call: Call<List<Person>>, t: Throwable) {
                // Handle failure
            }
        })
    }

    fun getCachedPersons(): List<Person> {
        return personDao.getAllPersons()
    }
}