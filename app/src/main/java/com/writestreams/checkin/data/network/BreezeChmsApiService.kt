package com.writestreams.checkin.data.network

import com.writestreams.checkin.data.local.Person
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface BreezeChmsApiService {
    @Headers("Api-Key: ...")
    @GET("people")
    fun getPersons(@Query("details") details: Int = 1): Call<List<Person>>
}