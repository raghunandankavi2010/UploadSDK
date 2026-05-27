package com.uploadsdk.presentation

import android.content.Context
import com.uploadsdk.config.UploadConfig
import com.uploadsdk.domain.model.UploadPriority
import com.uploadsdk.domain.model.UploadResult
import com.uploadsdk.domain.model.UploadTask
import kotlinx.coroutines.flow.Flow
import java.io.File

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
        fun create(
            context: Context,
            config: UploadConfig = UploadConfig(baseUrl = "http://10.0.2.2:5000/api/v1/")
        ): UploadSdk {
            val database = androidx.room.Room.databaseBuilder(
                context,
                com.uploadsdk.data.local.UploadDatabase::class.java,
                "upload_sdk_db"
            ).build()

            val apiService: com.uploadsdk.data.remote.api.UploadApiService = if (config.useMockApi) {
                com.uploadsdk.data.remote.api.MockUploadApiService()
            } else {
                val authInterceptor = com.uploadsdk.data.remote.interceptor.AuthInterceptor(config)
                val logging = okhttp3.logging.HttpLoggingInterceptor().apply {
                    level = okhttp3.logging.HttpLoggingInterceptor.Level.HEADERS
                }
                val client = okhttp3.OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .addInterceptor(logging)
                    .connectTimeout(config.timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(config.timeoutMs * 2, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .writeTimeout(config.timeoutMs * 2, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .retryOnConnectionFailure(true)
                    .build()
                retrofit2.Retrofit.Builder()
                    .baseUrl(config.baseUrl)
                    .client(client)
                    .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                    .build()
                    .create(com.uploadsdk.data.remote.api.UploadApiService::class.java)
            }

            val chunkEngine = com.uploadsdk.data.chunk.ChunkEngine(com.uploadsdk.data.preprocessor.ChecksumCalculator())
            val preprocessor = com.uploadsdk.data.preprocessor.FilePreprocessor(
                com.uploadsdk.data.preprocessor.ChecksumCalculator(),
                com.uploadsdk.data.preprocessor.ThumbnailGenerator(context),
                com.uploadsdk.data.preprocessor.UploadFileValidator()
            )
            val scheduler = com.uploadsdk.data.scheduler.UploadScheduler(
                context,
                com.uploadsdk.data.scheduler.BatteryAwareConstraint(context),
                com.uploadsdk.data.scheduler.ThermalThrottlingMonitor(context),
                config
            )
            val workObserver = com.uploadsdk.data.worker.UploadWorkObserver(context)
            val repository = com.uploadsdk.data.repository.UploadRepositoryImpl(
                context, database, preprocessor, chunkEngine, scheduler, workObserver, config
            )
            val useCase = com.uploadsdk.domain.usecase.UploadUseCase(repository)
            val uploadManager = UploadManager(useCase)

            return UploadSdk(uploadManager)
        }
    }
}
