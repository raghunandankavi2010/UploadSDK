package com.uploadsdk.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.uploadsdk.data.chunk.ChunkUploader
import com.uploadsdk.data.coordinator.RetryCoordinator
import com.uploadsdk.data.coordinator.SessionManager
import com.uploadsdk.data.local.UploadDatabase
import com.uploadsdk.data.remote.api.UploadApiService
import com.uploadsdk.data.remote.dto.CommitUploadRequest
import com.uploadsdk.domain.model.SessionInfo
import com.uploadsdk.util.UploadAnalytics
import com.uploadsdk.util.UploadSpeedCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.max
import kotlin.system.measureTimeMillis

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val apiService: UploadApiService,
    private val chunkUploader: ChunkUploader,
    private val sessionManager: SessionManager,
    private val retryCoordinator: RetryCoordinator,
    private val analytics: UploadAnalytics,
    private val database: UploadDatabase
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_CHUNK_SIZE = "chunk_size"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_TOTAL_CHUNKS = "total_chunks"
        const val KEY_CHECKSUM = "checksum"
        const val KEY_IS_RESUME = "is_resume"

        const val PROGRESS = "progress"
        const val BYTES_UPLOADED = "bytes_uploaded"
        const val TOTAL_BYTES = "total_bytes"
        const val CURRENT_CHUNK = "current_chunk"
        const val TOTAL_CHUNKS = "total_chunks"
        const val SPEED_KBPS = "speed_kbps"

        const val NOTIFICATION_CHANNEL_ID = "upload_sdk_channel"
        const val NOTIFICATION_CHANNEL_NAME = "File Uploads"
        const val NOTIFICATION_ID_BASE = 1000
    }

    private val taskDao = database.uploadTaskDao()
    private val chunkDao = database.chunkDao()
    private val speedCalculator = UploadSpeedCalculator()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "application/octet-stream"
        val totalBytes = inputData.getLong(KEY_TOTAL_BYTES, 0L)
        val totalChunks = inputData.getInt(KEY_TOTAL_CHUNKS, 0)
        val checksum = inputData.getString(KEY_CHECKSUM) ?: ""
        val isResume = inputData.getBoolean(KEY_IS_RESUME, false)

        val file = File(filePath)
        if (!file.exists()) {
            taskDao.markFailed(taskId, "File not found: $filePath")
            return Result.failure()
        }

        // Show notification immediately at 0%
        setForeground(createForegroundInfo(taskId, fileName, 0, 0, totalBytes))

        val startTime = System.currentTimeMillis()
        analytics.trackUploadStart(taskId, totalBytes, mimeType)

        return withContext(Dispatchers.IO) {
            try {
                taskDao.updateStatus(taskId, "IN_PROGRESS")

                val session = if (isResume) {
                    sessionManager.getSession(taskId) ?: sessionManager.createSession(
                        taskId, fileName, totalBytes, totalChunks, checksum
                    )
                } else {
                    sessionManager.createSession(taskId, fileName, totalBytes, totalChunks, checksum)
                }

                var uploadedChunks = chunkDao.getUploadedCount(taskId)
                var bytesUploaded = uploadedChunks * (totalBytes / max(totalChunks, 1))

                while (true) {
                    val chunk = chunkDao.getNextPendingChunk(taskId) ?: break

                    val chunkStartTime = System.currentTimeMillis()
                    val result = retryCoordinator.executeWithRetry(
                        maxRetries = 3,
                        isRetryable = { it is UnknownHostException || it is SocketTimeoutException }
                    ) {
                        chunkUploader.uploadChunk(
                            file = file,
                            chunk = chunk,
                            taskId = taskId,
                            sessionId = session.sessionId,
                            totalChunks = totalChunks,
                            mimeType = mimeType
                        )
                    }

                    if (result.isFailure) {
                        throw result.exceptionOrNull() ?: Exception("Chunk upload failed")
                    }

                    uploadedChunks++
                    bytesUploaded += chunk.size
                    val speedKbps = speedCalculator.onProgress(bytesUploaded)

                    val chunkDuration = System.currentTimeMillis() - chunkStartTime
                    analytics.trackChunkUpload(taskId, chunk.chunkIndex, chunkDuration, true)

                    val progress = (uploadedChunks * 100 / totalChunks)
                    taskDao.updateProgress(
                        taskId, progress, bytesUploaded, uploadedChunks, totalChunks, speedKbps
                    )

                    setProgress(
                        workDataOf(
                            PROGRESS to progress,
                            BYTES_UPLOADED to bytesUploaded,
                            TOTAL_BYTES to totalBytes,
                            CURRENT_CHUNK to uploadedChunks,
                            TOTAL_CHUNKS to totalChunks,
                            SPEED_KBPS to speedKbps
                        )
                    )

                    // UPDATE NOTIFICATION AFTER EACH CHUNK
                    setForeground(createForegroundInfo(taskId, fileName, progress, bytesUploaded, totalBytes))
                }

                taskDao.updateStatus(taskId, "VERIFYING")
                val commitRequest = CommitUploadRequest(
                    taskId = taskId,
                    sessionId = session.sessionId,
                    fileName = fileName,
                    mimeType = mimeType,
                    totalChunks = totalChunks,
                    checksum = checksum
                )

                val commitResponse = retryCoordinator.executeWithRetry(maxRetries = 3) {
                    apiService.commitUpload(commitRequest)
                }

                if (commitResponse.isSuccessful) {
                    val body = commitResponse.body()
                    if (body?.success == true) {
                        val duration = System.currentTimeMillis() - startTime
                        taskDao.markCompleted(taskId, body.remoteUrl, body.fileId)
                        analytics.trackUploadSuccess(taskId, duration, totalBytes)

                        // Show completion notification
                        showCompleteNotification(taskId, fileName)

                        Result.success(
                            workDataOf(
                                "remote_url" to body.remoteUrl,
                                "file_id" to (body.fileId ?: "")
                            )
                        )
                    } else {
                        throw Exception("Commit failed: ${body?.message}")
                    }
                } else {
                    throw Exception("Commit HTTP error: ${commitResponse.code()}")
                }

            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                val retryCount = taskDao.getById(taskId)?.retryCount ?: 0
                analytics.trackUploadFailure(taskId, e.message ?: "Unknown", retryCount)
                showErrorNotification(taskId, fileName, e.message ?: "Error")

                when (e) {
                    is UnknownHostException,
                    is SocketTimeoutException -> {
                        taskDao.markFailed(taskId, "Network error: ${e.message}")
                        Result.retry()
                    }
                    else -> {
                        taskDao.markFailed(taskId, "Error: ${e.message}")
                        Result.failure()
                    }
                }
            }
        }
    }

    private fun createForegroundInfo(
        taskId: String,
        fileName: String,
        progress: Int,
        bytesUploaded: Long,
        totalBytes: Long
    ): ForegroundInfo {
        createNotificationChannel()

        val notificationId = NOTIFICATION_ID_BASE + taskId.hashCode()

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Uploading $fileName")
            .setContentText("${formatBytes(bytesUploaded)} / ${formatBytes(totalBytes)}  ($progress%)")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun showCompleteNotification(taskId: String, fileName: String) {
        createNotificationChannel()
        val notificationId = NOTIFICATION_ID_BASE + taskId.hashCode()

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Upload Complete")
            .setContentText("$fileName uploaded successfully")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun showErrorNotification(taskId: String, fileName: String, error: String) {
        createNotificationChannel()
        val notificationId = NOTIFICATION_ID_BASE + taskId.hashCode()

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Upload Failed")
            .setContentText("$fileName: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of file uploads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }
}