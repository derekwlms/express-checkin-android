package com.writestreams.checkin.ui.checkin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CheckinViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Check In"
    }
    val text: LiveData<String> = _text
}