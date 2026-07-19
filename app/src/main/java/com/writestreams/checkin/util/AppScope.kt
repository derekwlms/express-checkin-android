package com.writestreams.checkin.util

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The single application-level scope for fire-and-forget work that must
 * survive screen and dialog dismissal (check-in flows keep printing labels and
 * pushing to Breeze after the dialog closes). Supervised so one failure never
 * cancels sibling work, with a handler so nothing fails silently.
 */
object AppScope {
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e("AppScope", "Uncaught coroutine failure", e)
    }

    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
}
