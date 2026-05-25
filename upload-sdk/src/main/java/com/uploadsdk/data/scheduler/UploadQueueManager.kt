package com.uploadsdk.data.scheduler

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.uploadsdk.util.UploadLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadQueueManager @Inject constructor(
    private val workManager: WorkManager
) {
    companion object {
        const val MAX_CONCURRENT_UPLOADS = 3
    }

    fun getActiveUploadCount(): Flow<Int> {
        return workManager.getWorkInfosByTagFlow("upload_task")
            .map { workInfos ->
                workInfos.count {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                }
            }
    }

    fun canEnqueueNewUpload(): Flow<Boolean> {
        return getActiveUploadCount().map { count ->
            count < MAX_CONCURRENT_UPLOADS
        }
    }

    suspend fun waitForSlot(): Boolean {
        var attempts = 0
        while (attempts < 30) {
            val activeCount = workManager.getWorkInfosByTag("upload_task").get()
                .count { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            if (activeCount < MAX_CONCURRENT_UPLOADS) {
                return true
            }
            UploadLogger.d("Queue full ($activeCount active), waiting for slot...")
            kotlinx.coroutines.delay(1000)
            attempts++
        }
        return false
    }
}
