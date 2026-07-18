package com.writestreams.checkin.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.writestreams.checkin.data.local.Checkin
import com.writestreams.checkin.data.network.BreezeChmsApiService
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.util.ApiKeys.BREEZE_API_URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class SyncStatus(
    val breezeReachable: Boolean? = null,   // null until the first probe completes
    val lastAttendanceSync: LocalDateTime? = null,
    val lastPeopleSync: LocalDateTime? = null,
    val pendingPushCount: Int = 0
)

/**
 * When the app is visible/foreground, periodically probe Breeze,
 * push anything pending, reconcile the local check-in ledger with the Breeze
 * attendance list, and refresh the people mirror on a slower cadence.
 */
class SyncEngine private constructor(private val context: Context) {

    private val repository = Repository(context)
    private val checkinService = CheckinService(context)
    private val apiService: BreezeChmsApiService
    private val probeApiService: BreezeChmsApiService

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncTrigger = Channel<Unit>(Channel.CONFLATED)
    private var loopJob: Job? = null
    private var lastPeopleSync: LocalDateTime? = null

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private var networkCallbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Only a hint to try sooner - the Breeze probe stays the source of truth.
            requestSyncNow()
        }
    }

    init {
        fun retrofitWithTimeout(timeoutSeconds: Long): BreezeChmsApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .client(client)
                .baseUrl(BREEZE_API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BreezeChmsApiService::class.java)
        }
        apiService = retrofitWithTimeout(30)
        probeApiService = retrofitWithTimeout(PROBE_TIMEOUT_SECONDS)
    }

    fun start() {
        if (!networkCallbackRegistered) {
            try {
                connectivityManager?.registerDefaultNetworkCallback(networkCallback)
                networkCallbackRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Unable to register network callback", e)
            }
        }
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                val reachable = try {
                    runSyncCycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Sync cycle failed", e)
                    false
                }
                val delayMs = if (reachable) FAST_POLL_MS else UNREACHABLE_POLL_MS
                withTimeoutOrNull(delayMs) { syncTrigger.receive() }
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        if (networkCallbackRegistered) {
            try {
                connectivityManager?.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to unregister network callback", e)
            }
            networkCallbackRegistered = false
        }
    }

    fun requestSyncNow() {
        syncTrigger.trySend(Unit)
    }

    private suspend fun runSyncCycle(): Boolean {
        val reachable = probeBreeze()
        if (!reachable) {
            updateStatus(reachable = false)
            return false
        }
        try {
            checkinService.sendPendingNewPersonsToBreeze()
            checkinService.sendPendingCheckinsToBreeze()
        } catch (e: Exception) {
            Log.e(TAG, "Pushing pending data failed", e)
        }
        val attendanceSynced = try {
            syncAttendance()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Attendance sync failed", e)
            false
        }
        val peopleSyncDue = lastPeopleSync
            ?.isBefore(LocalDateTime.now().minusMinutes(PEOPLE_SYNC_INTERVAL_MINUTES)) ?: true
        if (peopleSyncDue) {
            try {
                repository.fetchAndCachePersons()
                lastPeopleSync = LocalDateTime.now()
                Log.i(TAG, "Refreshed the people mirror from Breeze")
            } catch (e: Exception) {
                Log.e(TAG, "People refresh failed", e)
            }
        }
        updateStatus(reachable = true, attendanceSynced = attendanceSynced)
        return true
    }

    private suspend fun probeBreeze(): Boolean {
        return try {
            probeApiService.getProfileFields().isSuccessful
        } catch (e: Exception) {
            Log.d(TAG, "Breeze probe failed: ${e.message}")
            false
        }
    }

    // The fast path: the per-instance attendance list is tiny, so polling it often
    // propagates check-ins, checkouts, and even new guest families (via targeted
    // person fetches) between tablets within about a poll interval.
    private suspend fun syncAttendance() {
        val instanceId = SettingsService.currentBreezeInstanceId(context)
        val response = apiService.getAttendance(instanceId)
        if (!response.isSuccessful) {
            throw IllegalStateException("Attendance list returned ${response.code()}")
        }
        val records = response.body() ?: emptyList()
        val checkedInIds = records
            .filter { !isCheckedOut(it.check_out) }
            .map { it.person_id }
            .toSet()
        val checkinTimes = records.associate { it.person_id to parseBreezeDateTime(it.created_on) }

        val actions = AttendanceMerge.plan(
            localCheckins = repository.getCheckinsForInstance(instanceId),
            localPersonIds = repository.getAllPersonIds(),
            breezeCheckedInIds = checkedInIds
        )
        val now = LocalDateTime.now()
        actions.markSynced.forEach {
            repository.setCheckedInWithBreeze(it.personId, it.instanceId, now)
        }
        actions.remove.forEach {
            Log.i(TAG, "Person ${it.personId} was checked out remotely")
            repository.deleteCheckin(it.personId, it.instanceId)
        }
        actions.record.forEach { personId ->
            repository.checkIn(Checkin(personId, instanceId,
                checkinTimes[personId] ?: now, null, null, now))
        }
        actions.fetch.forEach { personId ->
            try {
                val person = apiService.getPerson(personId).execute().body()
                if (person != null) {
                    repository.addPerson(person)
                    repository.checkIn(Checkin(personId, instanceId,
                        checkinTimes[personId] ?: now, null, null, now))
                    Log.i(TAG, "Fetched new person $personId seen in Breeze attendance")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to fetch person $personId from Breeze", e)
            }
        }
    }

    private suspend fun updateStatus(reachable: Boolean, attendanceSynced: Boolean = false) {
        val pendingCount = try {
            repository.countPendingPushes()
        } catch (e: Exception) {
            _status.value.pendingPushCount
        }
        _status.value = SyncStatus(
            breezeReachable = reachable,
            lastAttendanceSync = if (attendanceSynced) LocalDateTime.now() else _status.value.lastAttendanceSync,
            lastPeopleSync = lastPeopleSync,
            pendingPushCount = pendingCount
        )
    }

    companion object {
        private const val TAG = "SyncEngine"
        private const val FAST_POLL_MS = 60_000L            // attendance fast path
        private const val UNREACHABLE_POLL_MS = 600_000L    // polite backoff while blocked/offline
        private const val PEOPLE_SYNC_INTERVAL_MINUTES = 15L
        private const val PROBE_TIMEOUT_SECONDS = 60L

        private val BREEZE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        @Volatile
        private var INSTANCE: SyncEngine? = null

        fun getInstance(context: Context): SyncEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncEngine(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun isCheckedOut(checkOut: String?): Boolean {
            return !checkOut.isNullOrBlank() && !checkOut.startsWith("0000")
        }

        fun parseBreezeDateTime(value: String?): LocalDateTime? {
            if (value.isNullOrBlank()) return null
            return runCatching { LocalDateTime.parse(value, BREEZE_DATE_TIME_FORMATTER) }.getOrNull()
        }
    }
}
