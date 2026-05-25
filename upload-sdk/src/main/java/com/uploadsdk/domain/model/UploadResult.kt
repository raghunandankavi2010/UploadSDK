package com.uploadsdk.domain.model

sealed class UploadResult {
    data class Enqueued(val taskId: String) : UploadResult()
    data class Preprocessing(val taskId: String, val stage: String) : UploadResult()
    data class Progress(
        val taskId: String,
        val percent: Int,
        val bytesUploaded: Long,
        val totalBytes: Long,
        val speedKbps: Double = 0.0
    ) : UploadResult()
    data class Success(
        val taskId: String,
        val remoteUrl: String,
        val fileId: String? = null,
        val bytesUploaded: Long
    ) : UploadResult()
    data class Failure(
        val taskId: String,
        val error: String,
        val isRetryable: Boolean = true
    ) : UploadResult()
    data class Cancelled(val taskId: String) : UploadResult()
    data class Paused(val taskId: String, val percent: Int) : UploadResult()
}
