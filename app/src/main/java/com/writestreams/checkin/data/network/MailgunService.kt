package com.writestreams.checkin.data.network

import retrofit2.Call
import retrofit2.http.*

interface MailgunService {
    @FormUrlEncoded
    @POST("messages")
    fun sendEmail(
        @Header("Authorization") authorization: String,
        @Field("from") from: String,
        @Field("to") to: String,
        @Field("subject") subject: String,
        @Field("text") text: String,
        @Field("html") html: String
    ): Call<Void>
}