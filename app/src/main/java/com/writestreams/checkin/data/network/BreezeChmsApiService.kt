package com.writestreams.checkin.data.network

import com.writestreams.checkin.data.local.Person
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers

interface BreezeChmsApiService {
    @Headers("Api-Key: ...")
    @GET("people")
    fun getPersons(): Call<List<Person>>
}