package com.uploadsdk.domain.repository

import com.uploadsdk.domain.model.UploadResult
import com.uploadsdk.domain.model.UploadTask
import kotlinx.coroutines.flow.Flow

interface UploadRepository {
    suspend fun enqueueUpload(task: UploadTask): String
    suspend fun cancelUpload(taskId: String)
    suspend fun pauseUpload(taskId: String)
    suspend fun resumeUpload(taskId: String)
    suspend fun retryUpload(taskId: String)
    fun observeUpload(taskId: String): Flow<UploadResult>
    fun observeAllUploads(): Flow<List<UploadResult>>
    suspend fun getPendingUploads(): List<UploadTask>
    suspend fun clearCompletedUploads()
    suspend fun getUploadHistory(): List<UploadTask>
}
