package com.uploadsdk.presentation

import com.uploadsdk.domain.model.ChunkInfo
import com.uploadsdk.domain.model.UploadPriority
import com.uploadsdk.domain.model.UploadResult
import com.uploadsdk.domain.model.UploadTask
import com.uploadsdk.domain.usecase.UploadUseCase
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadManager @Inject constructor(
    private val uploadUseCase: UploadUseCase
) {

    suspend fun uploadFile(
        file: File,
        fileName: String? = null,
        mimeType: String? = null,
        priority: UploadPriority = UploadPriority.NORMAL,
        metadata: Map<String, String> = emptyMap(),
        chunkSize: Int = UploadTask.DEFAULT_CHUNK_SIZE,
        maxRetries: Int = UploadTask.DEFAULT_MAX_RETRIES,
        generateThumbnail: Boolean = true
    ): String {
        val task = UploadTask(
            taskId = UUID.randomUUID().toString(),
            file = file,
            fileName = fileName ?: file.name,
            mimeType = mimeType ?: "application/octet-stream",
            metadata = metadata,
            chunkSize = chunkSize,
            priority = priority,
            maxRetries = maxRetries,
            generateThumbnail = generateThumbnail
        )
        return uploadUseCase(task)
    }

    fun observeUpload(taskId: String): Flow<UploadResult> {
        return uploadUseCase.observeUpload(taskId)
    }

    fun observeAllUploads(): Flow<List<UploadResult>> {
        return uploadUseCase.observeAll()
    }

    suspend fun cancel(taskId: String) {
        uploadUseCase.cancel(taskId)
    }

    suspend fun delete(taskId: String) {
        uploadUseCase.delete(taskId)
    }

    suspend fun pause(taskId: String) {
        uploadUseCase.pause(taskId)
    }

    suspend fun resume(taskId: String) {
        uploadUseCase.resume(taskId)
    }

    suspend fun retry(taskId: String) {
        uploadUseCase.retry(taskId)
    }

    suspend fun getPendingUploads(): List<UploadTask> {
        return uploadUseCase.getPending()
    }

    suspend fun clearCompleted() {
        uploadUseCase.clearCompleted()
    }

    suspend fun getChunkProgress(taskId: String): List<ChunkInfo> {
        return uploadUseCase.getChunkProgress(taskId)
    }
}
