package com.uploadsdk.data.worker

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.uploadsdk.domain.model.UploadResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadWorkObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun observeWorkProgress(taskId: String): Flow<UploadResult.Progress> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("upload_$taskId")
            .mapNotNull { workInfos ->
                val workInfo = workInfos.firstOrNull() ?: return@mapNotNull null
                val progress = workInfo.progress
                val percent = progress.getInt(UploadWorker.PROGRESS, 0)
                val bytes = progress.getLong(UploadWorker.BYTES_UPLOADED, 0L)
                val total = progress.getLong(UploadWorker.TOTAL_BYTES, 0L)
                val speed = progress.getDouble(UploadWorker.SPEED_KBPS, 0.0)
                val eta = progress.getLong(UploadWorker.ETA_SECONDS, -1L)

                UploadResult.Progress(
                    taskId = taskId,
                    fileName = "",
                    percent = percent,
                    bytesUploaded = bytes,
                    totalBytes = total,
                    speedKbps = speed,
                    etaSeconds = eta
                )
            }
    }

    fun observeWorkState(taskId: String): Flow<WorkInfo.State> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("upload_$taskId")
            .mapNotNull { workInfos ->
                workInfos.firstOrNull()?.state
            }
    }

    suspend fun isWorkRunning(taskId: String): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork("upload_$taskId")
                .get()
            workInfos.any {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }
        }
    }
}
