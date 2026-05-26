package com.uploadsdk.presentation

import android.content.Context
import com.uploadsdk.data.local.UploadDatabase
import com.uploadsdk.data.remote.api.MockUploadApiService
import com.uploadsdk.domain.model.UploadPriority
import com.uploadsdk.domain.model.UploadResult
import com.uploadsdk.domain.model.UploadTask
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Non-Hilt entry point for the Upload SDK.
 * Use this if you don't use Hilt/Dagger in your project.
 *
 * Example:
 * ```kotlin
 * val uploadSdk = UploadSdk.create(context)
 * val taskId = uploadSdk.uploadFile(file, priority = UploadPriority.HIGH)
 * ```
 */
class UploadSdk private constructor(
    private val uploadManager: UploadManager
) {

    suspend fun uploadFile(
        file: File,
        fileName: String? = null,
        mimeType: String? = null,
        priority: UploadPriority = UploadPriority.NORMAL,
        metadata: Map<String, String> = emptyMap()
    ): String {
        return uploadManager.uploadFile(file, fileName, mimeType, priority, metadata)
    }

    fun observeUpload(taskId: String): Flow<UploadResult> {
        return uploadManager.observeUpload(taskId)
    }

    fun observeAllUploads(): Flow<List<UploadResult>> {
        return uploadManager.observeAllUploads()
    }

    suspend fun pause(taskId: String) = uploadManager.pause(taskId)
    suspend fun resume(taskId: String) = uploadManager.resume(taskId)
    suspend fun cancel(taskId: String) = uploadManager.cancel(taskId)
    suspend fun retry(taskId: String) = uploadManager.retry(taskId)

    companion object {
        fun create(context: Context): UploadSdk {
            // Simplified initialization without Hilt
            // In production, use Hilt for proper dependency injection
            val database = androidx.room.Room.databaseBuilder(
                context,
                com.uploadsdk.data.local.UploadDatabase::class.java,
                "upload_sdk_db"
            ).build()

            val apiService = MockUploadApiService()
            val chunkUploader = com.uploadsdk.data.chunk.ChunkUploader(apiService, database.chunkDao())
            val sessionManager = com.uploadsdk.data.coordinator.SessionManager(apiService, database.sessionDao())
            val retryCoordinator = com.uploadsdk.data.coordinator.RetryCoordinator()
            val notificationManager = com.uploadsdk.data.worker.UploadNotificationManager(context)
            val analytics = com.uploadsdk.util.NoOpUploadAnalytics()
            val chunkEngine = com.uploadsdk.data.chunk.ChunkEngine()
            val preprocessor = com.uploadsdk.data.preprocessor.FilePreprocessor(
                com.uploadsdk.data.preprocessor.ChecksumCalculator(),
                com.uploadsdk.data.preprocessor.ThumbnailGenerator(context)
            )
            val scheduler = com.uploadsdk.data.scheduler.UploadScheduler(
                context,
                com.uploadsdk.data.scheduler.BatteryAwareConstraint(context),
                com.uploadsdk.data.scheduler.ThermalThrottlingMonitor(context)
            )
            val workObserver = com.uploadsdk.data.worker.UploadWorkObserver(context)
            val repository = com.uploadsdk.data.repository.UploadRepositoryImpl(
                context, database, preprocessor, chunkEngine, scheduler, workObserver
            )
            val useCase = com.uploadsdk.domain.usecase.UploadUseCase(repository)
            val uploadManager = UploadManager(useCase)

            return UploadSdk(uploadManager)
        }
    }
}
