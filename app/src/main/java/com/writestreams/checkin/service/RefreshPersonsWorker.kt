package com.writestreams.checkin.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.writestreams.checkin.data.repository.Repository

/**
 * Background "nuke and re-fetch" of the members mirror: download the full
 * directory from Breeze first, then swap it in atomically
 * Runs via WorkManager so it continues while staff navigate the app;
 * the check-in ledger and offline-created people are untouched.
 */
class RefreshPersonsWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val count = Repository(applicationContext).refreshAllPersonsFromBreeze()
            Log.i(TAG, "Replaced the members mirror with $count persons from Breeze")
            Result.success(workDataOf(KEY_COUNT to count))
        } catch (e: Exception) {
            Log.e(TAG, "Refreshing the members mirror failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "RefreshPersonsWorker"
        const val WORK_NAME = "refresh_persons"
        const val KEY_COUNT = "count"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshPersonsWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
