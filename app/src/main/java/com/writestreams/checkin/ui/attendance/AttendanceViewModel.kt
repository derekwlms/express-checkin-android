package com.writestreams.checkin.ui.attendance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.service.AttendanceService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the attendance list. LOCAL and PENDING observe the Room checkins
 * table, so the screen updates itself whenever a check-in happens on this
 * tablet or the sync engine brings one in from Breeze. BREEZE is an on-demand
 * query of the live attendance list.
 */
class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    enum class Source { LOCAL, PENDING, BREEZE }

    private val repository = Repository(application)
    private val attendanceService = AttendanceService(application)

    private val source = MutableStateFlow(Source.LOCAL)

    @OptIn(ExperimentalCoroutinesApi::class)
    val persons: StateFlow<List<Person>> = source
        .flatMapLatest { selected ->
            when (selected) {
                Source.LOCAL -> repository.observeCheckedInPersons()
                Source.PENDING -> repository.observePendingCheckedInPersons()
                Source.BREEZE -> flow { emit(attendanceService.getBreezeCheckedInPersons()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSource(selected: Source) {
        source.value = selected
    }
}
