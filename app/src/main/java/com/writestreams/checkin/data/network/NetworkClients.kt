package com.writestreams.checkin.data.network

import com.writestreams.checkin.util.ApiKeys.BREEZE_API_URL
import com.writestreams.checkin.util.ApiKeys.MAILGUN_URL
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The single place Retrofit/OkHttp stacks are built. Each service is created
 * once and shared by every consumer (Repository, CheckinService,
 * AttendanceService, SyncEngine).
 */
object NetworkClients {

    private const val DEFAULT_TIMEOUT_SECONDS = 30L

    // The reachability probe gets its own client so its timeout can differ
    // from normal API calls (see SyncEngine).
    const val PROBE_TIMEOUT_SECONDS = 60L

    val breezeApiService: BreezeChmsApiService by lazy {
        buildService(BREEZE_API_URL, DEFAULT_TIMEOUT_SECONDS)
    }

    val breezeProbeApiService: BreezeChmsApiService by lazy {
        buildService(BREEZE_API_URL, PROBE_TIMEOUT_SECONDS)
    }

    val mailgunService: MailgunService by lazy {
        buildService(MAILGUN_URL, DEFAULT_TIMEOUT_SECONDS)
    }

    private inline fun <reified T> buildService(baseUrl: String, timeoutSeconds: Long): T {
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .client(client)
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(T::class.java)
    }
}
