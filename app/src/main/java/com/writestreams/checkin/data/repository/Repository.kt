package com.writestreams.checkin.data.repository

import android.content.Context
import com.writestreams.checkin.data.local.AppDatabase
import com.writestreams.checkin.data.local.Checkin
import com.writestreams.checkin.data.local.CheckinDao
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.data.local.PersonDao
import com.writestreams.checkin.data.network.BreezeChmsApiService
import com.writestreams.checkin.service.OfflineFamily
import com.writestreams.checkin.service.PendingItem
import com.writestreams.checkin.service.PendingItems
import com.writestreams.checkin.service.SettingsService
import com.writestreams.checkin.util.ApiKeys.BREEZE_API_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Repository(private val context: Context) {
    private val apiService: BreezeChmsApiService
    private val personDao: PersonDao
    private val checkinDao: CheckinDao

    companion object {
        // Guards the read-max-then-insert sequence for check-in counters
        // across all Repository instances
        private val checkinCounterMutex = Mutex()
    }

    init {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(BREEZE_API_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(BreezeChmsApiService::class.java)

        val db = AppDatabase.getDatabase(context)
        personDao = db.personDao()
        checkinDao = db.checkinDao()
    }

    private fun currentInstanceId(): String =
        SettingsService.currentBreezeInstanceId(context)

    private suspend fun fetchPersonsFromBreeze(): List<Person> {
        return withContext(Dispatchers.IO) {
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
    }

    suspend fun fetchAndCachePersons() {
        val persons = fetchPersonsFromBreeze()
        withContext(Dispatchers.IO) {
            personDao.insertAll(persons)
        }
    }

    // Full download first, then swap the mirror in one transaction
    // so that interrupted fetch leaves the old data intact
    suspend fun refreshAllPersonsFromBreeze(): Int {
        val persons = fetchPersonsFromBreeze()
        withContext(Dispatchers.IO) {
            personDao.replaceAllFromBreeze(persons)
        }
        return persons.size
    }

    suspend fun resetAllCheckins() {
        return withContext(Dispatchers.IO) {
            checkinDao.deleteAll()
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
            attachPersons(checkinDao.getCheckedInForInstance(currentInstanceId()))
        }
    }

    // Observable variants: Room re-emits whenever the checkins table changes,
    // so screens update when the sync engine (or a check-in here) writes to the database
    fun observeCheckedInPersons(): Flow<List<Person>> {
        return checkinDao.observeCheckedInForInstance(currentInstanceId())
            .map { attachPersons(it) }
            .flowOn(Dispatchers.IO)
    }

    // Everything not yet in Breeze (new people, check-ins, check-outs; any event), one item per person
    fun observePendingItems(): Flow<List<PendingItem>> {
        return combine(checkinDao.observeUnsent(), personDao.observePendingNewPersons()) { checkins, newPersons ->
            PendingItems.build(checkins, newPersons, ::nameOf, currentInstanceId())
        }.flowOn(Dispatchers.IO)
    }

    private fun nameOf(personId: String): String? = personDao.getPersonById(personId)?.fullName()

    // Join checkin rows to their persons, carrying check-in state on the
    // transient Person fields for display
    private fun attachPersons(checkins: List<Checkin>): List<Person> {
        return checkins.mapNotNull { checkin ->
            personDao.getPersonById(checkin.personId)?.also {
                it.checkinDateTime = checkin.checkinDateTime
                it.checkinCode = checkin.checkinCode
                it.checkinCounter = checkin.checkinCounter
                it.breezeSyncDateTime = checkin.breezeSyncDateTime
            }
        }
    }

    suspend fun getPendingNewPersons(): List<Person> {
        return withContext(Dispatchers.IO) {
            personDao.getPendingNewPersons()
        }
    }

    suspend fun getAllPendingCheckins(): List<Checkin> {
        return withContext(Dispatchers.IO) {
            checkinDao.getAllPending()
        }
    }

    suspend fun getCheckinsForInstance(instanceId: String): List<Checkin> {
        return withContext(Dispatchers.IO) {
            checkinDao.getForInstance(instanceId)
        }
    }

    suspend fun getAllPersonIds(): Set<String> {
        return withContext(Dispatchers.IO) {
            personDao.getAllPersonIds().toSet()
        }
    }

    // Number of people with anything not yet in Breeze (matches the Pending tab)
    suspend fun countPendingPushes(): Int {
        return withContext(Dispatchers.IO) {
            PendingItems.build(checkinDao.getUnsent(), personDao.getPendingNewPersons(),
                ::nameOf, currentInstanceId()).size
        }
    }

    suspend fun deleteCheckin(personId: String, instanceId: String) {
        withContext(Dispatchers.IO) {
            checkinDao.delete(personId, instanceId)
        }
    }

    suspend fun checkIn(checkin: Checkin) {
        withContext(Dispatchers.IO) {
            checkinDao.upsert(checkin)
        }
    }

    // Queues a check-out for the current event: the row stays, marked, until Breeze confirms it.
    // Returns the instance id of the marked row, or null if the person wasn't checked in here.
    suspend fun checkOut(personId: String): String? {
        return withContext(Dispatchers.IO) {
            val instanceId = currentInstanceId()
            if (checkinDao.markCheckedOut(personId, instanceId, LocalDateTime.now()) > 0) instanceId else null
        }
    }

    suspend fun getPendingCheckouts(): List<Checkin> {
        return withContext(Dispatchers.IO) {
            checkinDao.getPendingCheckouts()
        }
    }

    suspend fun nextCheckinCounter(): String {
        return checkinCounterMutex.withLock {
            withContext(Dispatchers.IO) {
                ((checkinDao.getMaxCheckinCounter() ?: 0) + 1).toString()
            }
        }
    }

    suspend fun setCheckedInWithBreeze(personId: String, instanceId: String, breezeSyncDateTime: LocalDateTime) {
        withContext(Dispatchers.IO) {
            checkinDao.setSynced(personId, instanceId, breezeSyncDateTime)
        }
    }

    suspend fun remapPersonCheckins(oldPersonId: String, newPersonId: String) {
        withContext(Dispatchers.IO) {
            checkinDao.remapPersonId(oldPersonId, newPersonId)
        }
    }

    suspend fun purgeSyncedCheckinsFromOtherInstances(instanceId: String) {
        withContext(Dispatchers.IO) {
            checkinDao.purgeSyncedFromOtherInstances(instanceId)
        }
    }

    suspend fun searchPersons(query: String): List<Person> {
        return withContext(Dispatchers.IO) {
            personDao.searchPersons("%$query%")
        }
    }

    // When an offline (OL_) person is pushed, point other pending persons' family links at the new Breeze id.
    suspend fun remapPendingFamilyReferences(oldPersonId: String, newPersonId: String) {
        withContext(Dispatchers.IO) {
            personDao.getPendingNewPersons()
                .filter { person -> person.family.any { it.person_id == oldPersonId } }
                .forEach { personDao.update(it.copy(family = OfflineFamily.remap(it.family, oldPersonId, newPersonId))) }
        }
    }

    suspend fun updatePerson(person: Person) {
        withContext(Dispatchers.IO) {
            personDao.update(person)
        }
    }

    suspend fun addPerson(person: Person) {
        withContext(Dispatchers.IO) {
            personDao.add(person)
        }
    }

    suspend fun deletePerson(person: Person) {
        withContext(Dispatchers.IO) {
            personDao.delete(person)
        }
    }

    suspend fun deleteAllPersons() {
        withContext(Dispatchers.IO) {
            personDao.deleteAll()
            checkinDao.deleteAll()
        }
    }
}
