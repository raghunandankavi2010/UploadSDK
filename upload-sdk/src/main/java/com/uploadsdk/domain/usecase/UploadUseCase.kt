package com.uploadsdk.domain.usecase

import com.uploadsdk.domain.model.UploadResult
import com.uploadsdk.domain.model.UploadTask
import com.uploadsdk.domain.repository.UploadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UploadUseCase @Inject constructor(
    private val repository: UploadRepository
) {
    suspend operator fun invoke(task: UploadTask): String {
        return repository.enqueueUpload(task)
    }

    fun observeUpload(taskId: String): Flow<UploadResult> {
        return repository.observeUpload(taskId)
    }

    suspend fun cancel(taskId: String) {
        repository.cancelUpload(taskId)
    }

    suspend fun pause(taskId: String) {
        repository.pauseUpload(taskId)
    }

    suspend fun resume(taskId: String) {
        repository.resumeUpload(taskId)
    }

    suspend fun retry(taskId: String) {
        repository.retryUpload(taskId)
    }

    fun observeAll(): Flow<List<UploadResult>> {
        return repository.observeAllUploads()
    }

    suspend fun getPending(): List<UploadTask> {
        return repository.getPendingUploads()
    }

    suspend fun clearCompleted() {
        repository.clearCompletedUploads()
    }
}
