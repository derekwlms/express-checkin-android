package com.writestreams.checkin.ui.attendance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AttendanceViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Attendance"
    }
    val text: LiveData<String> = _text
}