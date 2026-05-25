package com.uploadsdk.domain.model

sealed class UploadStatus {
    data object Pending : UploadStatus()
    data class Preprocessing(val stage: String) : UploadStatus()
    data class Queued(val position: Int) : UploadStatus()
    data class InProgress(
        val progressPercent: Int,
        val bytesUploaded: Long,
        val totalBytes: Long,
        val currentChunk: Int,
        val totalChunks: Int,
        val speedKbps: Double = 0.0
    ) : UploadStatus()
    data class Paused(val progressPercent: Int, val bytesUploaded: Long) : UploadStatus()
    data class Completed(val remoteUrl: String, val fileId: String? = null) : UploadStatus()
    data class Failed(val error: String, val retryCount: Int, val isRetryable: Boolean = true) : UploadStatus()
    data object Cancelled : UploadStatus()
    data class Verifying(val progressPercent: Int) : UploadStatus()
}
