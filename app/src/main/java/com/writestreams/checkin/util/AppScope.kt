package com.writestreams.checkin.util

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The application-level scope for fire-and-forget work that must outlive the screen or
 * dialog that started it (a check-in keeps printing labels and pushing to Breeze after
 * its dialog closes). Supervised, so one failure never cancels other work, and failures
 * are logged rather than vanishing.
 */
object AppScope {
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e("AppScope", "Uncaught coroutine failure", e)
    }

    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
}
