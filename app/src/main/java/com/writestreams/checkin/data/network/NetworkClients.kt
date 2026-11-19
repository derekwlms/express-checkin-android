package com.writestreams.checkin.data.network

import com.writestreams.checkin.util.ApiKeys.BREEZE_API_URL
import com.writestreams.checkin.util.ApiKeys.MAILGUN_URL
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The one place the Retrofit/OkHttp clients are built. Each is created once and shared
 * by Repository, CheckinService, AttendanceService and SyncEngine, keeping the timeouts
 * each of them used before.
 */
object NetworkClients {

    private const val BREEZE_TIMEOUT_SECONDS = 30L

    // The reachability probe has its own client so its timeout can differ (see SyncEngine)
    const val PROBE_TIMEOUT_SECONDS = 60L

    val breezeApiService: BreezeChmsApiService by lazy {
        build(BREEZE_API_URL, withTimeout(BREEZE_TIMEOUT_SECONDS))
    }

    val breezeProbeApiService: BreezeChmsApiService by lazy {
        build(BREEZE_API_URL, withTimeout(PROBE_TIMEOUT_SECONDS))
    }

    // Email is fire-and-forget, so it keeps OkHttp's default (10s) timeouts
    val mailgunService: MailgunService by lazy {
        build(MAILGUN_URL, OkHttpClient.Builder().build())
    }

    private fun withTimeout(seconds: Long): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(seconds, TimeUnit.SECONDS)
        .readTimeout(seconds, TimeUnit.SECONDS)
        .writeTimeout(seconds, TimeUnit.SECONDS)
        .build()

    private inline fun <reified T> build(baseUrl: String, client: OkHttpClient): T =
        Retrofit.Builder()
            .client(client)
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(T::class.java)
}
