package com.uploadsdk.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.uploadsdk.data.chunk.ChunkUploader
import com.uploadsdk.data.coordinator.RetryCoordinator
import com.uploadsdk.data.coordinator.SessionManager
import com.uploadsdk.data.local.UploadDatabase
import com.uploadsdk.data.local.dao.ChunkDao
import com.uploadsdk.data.local.dao.UploadTaskDao
import com.uploadsdk.data.remote.api.UploadApiService
import com.uploadsdk.data.remote.dto.CommitUploadRequest
import com.uploadsdk.domain.model.SessionInfo
import com.uploadsdk.util.UploadAnalytics
import com.uploadsdk.util.UploadLogger
import com.uploadsdk.util.UploadSpeedCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.min
import kotlin.system.measureTimeMillis

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val apiService: UploadApiService,
    private val chunkUploader: ChunkUploader,
    private val sessionManager: SessionManager,
    private val retryCoordinator: RetryCoordinator,
    private val notificationManager: UploadNotificationManager,
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
    }

    private val taskDao = database.uploadTaskDao()
    private val chunkDao = database.chunkDao()
    private val speedCalculator = UploadSpeedCalculator()

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        UploadLogger.d("UploadWorker: Starting work for task $taskId")
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "application/octet-stream"
        val totalBytes = inputData.getLong(KEY_TOTAL_BYTES, 0L)
        val totalChunks = inputData.getInt(KEY_TOTAL_CHUNKS, 0)
        val checksum = inputData.getString(KEY_CHECKSUM) ?: ""
        val isResume = inputData.getBoolean(KEY_IS_RESUME, false)

        val file = File(filePath)
        if (!file.exists()) {
            UploadLogger.e("UploadWorker: File not found: $filePath")
            taskDao.markFailed(taskId, "File not found: $filePath")
            notificationManager.showErrorNotification(taskId, fileName, "File not found")
            return Result.failure()
        }

        val startTime = System.currentTimeMillis()
        analytics.trackUploadStart(taskId, totalBytes, mimeType)

        return withContext(Dispatchers.IO) {
            try {
                UploadLogger.d("UploadWorker: Updating status to IN_PROGRESS for $taskId")
                taskDao.updateStatus(taskId, "IN_PROGRESS")

                // Get or create session
                UploadLogger.d("UploadWorker: Creating/getting session for $taskId")
                val session = if (isResume) {
                    sessionManager.getSession(taskId) ?: sessionManager.createSession(
                        taskId, fileName, totalBytes, checksum
                    )
                } else {
                    sessionManager.createSession(taskId, fileName, totalBytes, checksum)
                }
                UploadLogger.d("UploadWorker: Session created: ${session.sessionId}")

                var uploadedChunks = chunkDao.getUploadedCount(taskId)
                var bytesUploaded = uploadedChunks * (totalBytes / maxOf(totalChunks, 1))

                // Upload remaining chunks
                UploadLogger.d("UploadWorker: Starting chunk upload loop for $taskId")
                while (true) {
                    if (isStopped) {
                        UploadLogger.d("UploadWorker: Worker stopped for $taskId")
                        break
                    }

                    val chunk = chunkDao.getNextPendingChunk(taskId) ?: break
                    UploadLogger.d("UploadWorker: Uploading chunk ${chunk.chunkIndex} for $taskId")

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
                        UploadLogger.e("UploadWorker: Chunk upload failed for $taskId", result.exceptionOrNull())
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

                    notificationManager.showProgressNotification(
                        taskId, fileName, progress, bytesUploaded, totalBytes
                    )
                }

                if (isStopped) return@withContext Result.retry()

                // Commit upload
                UploadLogger.d("UploadWorker: Committing upload for $taskId")
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
                        UploadLogger.d("UploadWorker: Upload successful for $taskId")
                        val duration = System.currentTimeMillis() - startTime
                        taskDao.markCompleted(taskId, body.remoteUrl, body.fileId)
                        analytics.trackUploadSuccess(taskId, duration, totalBytes)
                        notificationManager.showCompleteNotification(taskId, fileName)
                        Result.success(
                            workDataOf(
                                "remote_url" to body.remoteUrl,
                                "file_id" to (body.fileId ?: "")
                            )
                        )
                    } else {
                        UploadLogger.e("UploadWorker: Commit failed for $taskId: ${body?.message}")
                        throw Exception("Commit failed: ${body?.message}")
                    }
                } else {
                    UploadLogger.e("UploadWorker: Commit HTTP error for $taskId: ${commitResponse.code()}")
                    throw Exception("Commit HTTP error: ${commitResponse.code()}")
                }

            } catch (e: CancellationException) {
                UploadLogger.d("Upload task $taskId cancelled/paused")
                Result.retry()
            } catch (e: Exception) {
                UploadLogger.e("UploadWorker: Error during upload for $taskId", e)
                val duration = System.currentTimeMillis() - startTime
                val retryCount = taskDao.getById(taskId)?.retryCount ?: 0
                analytics.trackUploadFailure(taskId, e.message ?: "Unknown", retryCount)
                notificationManager.showErrorNotification(taskId, fileName, e.message ?: "Error")

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
            } finally {
                if (taskDao.getById(taskId)?.statusType == "COMPLETED") {
                    notificationManager.cancelNotification(taskId)
                }
            }
        }
    }
}
