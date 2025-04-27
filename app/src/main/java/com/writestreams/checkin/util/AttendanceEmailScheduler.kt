package com.writestreams.checkin.util

import android.content.Context
import androidx.work.*
import com.writestreams.checkin.service.SendAttendanceEmailWorker
import java.util.concurrent.TimeUnit
import java.util.Calendar

object AttendanceEmailScheduler {

    fun scheduleWeeklyEmail(context: Context) {
        val now = Calendar.getInstance()
        val nextSunday = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 40)
            set(Calendar.SECOND, 0)
            if (before(now)) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }

        val initialDelay = nextSunday.timeInMillis - now.timeInMillis
        val workRequest = PeriodicWorkRequestBuilder<SendAttendanceEmailWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "SendAttendanceEmail",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }
}