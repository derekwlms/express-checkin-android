package com.writestreams.checkin.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.util.ApiKeys.EMAIL_RECIPIENTS

class SendAttendanceEmailWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val attendanceService = AttendanceService(context)
    private val repository = Repository(context)

    override suspend fun doWork(): Result {
        return try {
            val personsList = repository.getCheckedInPersons()
            val attendanceList = personsList.map { "${it.nameLastFirst()} - ${it.id} - ${it.getFormattedCheckinTime()}" }
            val recipient = EMAIL_RECIPIENTS
            attendanceService.emailAttendanceList(attendanceList, recipient)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
