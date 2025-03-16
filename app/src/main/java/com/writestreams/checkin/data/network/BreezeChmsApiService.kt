package com.writestreams.checkin.data.network

import com.google.gson.JsonObject
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.util.ApiKeys
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface BreezeChmsApiService {
    @Headers("Api-Key: ${ApiKeys.BREEZE_API_KEY}")
    @GET("people")
    fun getPersons(@Query("details") details: Int = 1): Call<List<Person>>

    @Headers("Api-Key: ${ApiKeys.BREEZE_API_KEY}")
    @GET("events/attendance/add")
    suspend fun checkIn(
        @Query("person_id") personId: String,
        @Query("instance_id") instanceId: String,
        @Query("direction") direction: String = "in"
    ): Response<Void>

    @Headers("Api-Key: ${ApiKeys.BREEZE_API_KEY}")
    @GET("events/attendance/delete")
    suspend fun deleteCheckin(
        @Query("person_id") personId: String,
        @Query("instance_id") instanceId: String
    ): Response<Void>

    @Headers("Api-Key: ${ApiKeys.BREEZE_API_KEY}")
    @GET("people/add")
    suspend fun addPerson(
        @Query("first") firstName: String,
        @Query("last") lastName: String,
        @Query("fields_json") fields: String
    ): Response<JsonObject>

    @Headers("Api-Key: ${ApiKeys.BREEZE_API_KEY}")
    @GET("people/update")
    suspend fun updatePerson(
        @Query("person_id") personId: String,
        @Query("fields_json") fields: String
    ): Response<JsonObject>
}