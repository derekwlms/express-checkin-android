package com.writestreams.checkin.ui.attendance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.service.CheckinService
import com.writestreams.checkin.service.PendingItems
import com.writestreams.checkin.service.SyncEngine
import com.writestreams.checkin.service.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter

/**
 * Drives the attendance list; both tabs observe Room, so they update themselves.
 *  - ALL: everyone checked in for the current event, from any tablet. The sync engine
 *    merges Breeze's attendance list in, so this is Breeze plus anything not sent yet.
 *  - PENDING: everything on this tablet not yet in Breeze (new people, check-ins,
 *    check-outs, from any event). Usually empty while Breeze is reachable.
 */
class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    enum class Source { ALL, PENDING }

    private val repository = Repository(application)
    private val syncStatus: StateFlow<SyncStatus> = SyncEngine.getInstance(application).status

    private val source = MutableStateFlow(Source.ALL)

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<AttendanceRow>> = source
        .flatMapLatest { selected ->
            when (selected) {
                Source.ALL -> repository.observeCheckedInPersons().map { persons ->
                    persons.sortedBy { it.nameLastFirst().lowercase() }.map { allRow(it) }
                }
                Source.PENDING -> combine(repository.observePendingItems(), syncStatus) { items, _ ->
                    PendingItems.withRejections(items, CheckinService.rejectionsById()).map {
                        AttendanceRow(key = it.personId, name = it.name, detail = it.detail)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statusText: StateFlow<String> = syncStatus
        .map { statusLine(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setSource(selected: Source) {
        source.value = selected
    }

    private fun allRow(person: Person): AttendanceRow {
        val parts = mutableListOf<String>()
        person.checkinDateTime?.let { parts.add(it.format(TIME_FORMAT)) }
        // Only check-ins made on this tablet carry a check-in code; rows merged in
        // from Breeze came from the other tablet or from Breeze itself.
        parts.add(if (person.checkinCode.isNullOrEmpty()) "elsewhere" else "this tablet")
        if (person.breezeSyncDateTime == null) parts.add("not in Breeze yet")
        return AttendanceRow(key = person.id, name = person.fullName(), detail = parts.joinToString(" · "),
            person = person)
    }

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")
        private val SYNC_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm:ss a")

        fun statusLine(status: SyncStatus): String {
            val lastSync = status.lastAttendanceSync?.format(SYNC_TIME_FORMAT)
            return when (status.breezeReachable) {
                true -> if (lastSync != null) "Synced with Breeze at $lastSync" else "Syncing with Breeze…"
                false -> "Breeze unreachable – showing " +
                    (if (lastSync != null) "the sync from $lastSync" else "no sync yet") +
                    " plus this tablet's check-ins"
                null -> "Checking Breeze…"
            }
        }
    }
}
