package com.writestreams.checkin.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.util.ApiKeys.EMAIL_RECIPIENTS
import kotlinx.coroutines.runBlocking

class SendAttendanceEmailWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private val attendanceService = AttendanceService(context)
    private val repository = Repository(context)

    override fun doWork(): Result {
        return try {
            runBlocking {
                val personsList = repository.getCheckedInPersons()
                val attendanceList = personsList.map { "${it.nameLastFirst()} - ${it.id}" }
                val recipient = EMAIL_RECIPIENTS
                attendanceService.emailAttendanceList(attendanceList, recipient)
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}