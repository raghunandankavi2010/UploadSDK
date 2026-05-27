package com.uploadsdk.data.scheduler

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.uploadsdk.config.UploadConfig
import com.uploadsdk.util.UploadLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadQueueManager @Inject constructor(
    private val workManager: WorkManager,
    private val config: UploadConfig
) {
    companion object {
        const val DEFAULT_MAX_CONCURRENT_UPLOADS = 3
    }

    val maxConcurrentUploads: Int
        get() = config.parallelUploads.coerceIn(1, 5)

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
            count < maxConcurrentUploads
        }
    }

    suspend fun getActiveCount(): Int = withContext(Dispatchers.IO) {
        workManager.getWorkInfosByTag("upload_task").get()
            .count { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    }

    suspend fun hasCapacity(): Boolean {
        return getActiveCount() < maxConcurrentUploads
    }
}
