package com.uploadsdk.util

import com.uploadsdk.domain.model.UploadStatus

object UploadStatusMapper {

    fun toString(status: UploadStatus): String {
        return when (status) {
            is UploadStatus.Pending -> "PENDING"
            is UploadStatus.Preprocessing -> "PREPROCESSING"
            is UploadStatus.Queued -> "QUEUED"
            is UploadStatus.InProgress -> "IN_PROGRESS"
            is UploadStatus.Paused -> "PAUSED"
            is UploadStatus.Verifying -> "VERIFYING"
            is UploadStatus.Completed -> "COMPLETED"
            is UploadStatus.Failed -> "FAILED"
            is UploadStatus.Cancelled -> "CANCELLED"
        }
    }

    fun fromString(
        statusType: String,
        progressPercent: Int = 0,
        bytesUploaded: Long = 0,
        totalBytes: Long = 0,
        currentChunk: Int = 0,
        totalChunks: Int = 0,
        speedKbps: Double = 0.0,
        remoteUrl: String? = null,
        fileId: String? = null,
        errorMessage: String? = null,
        retryCount: Int = 0,
        isRetryable: Boolean = true
    ): UploadStatus {
        return when (statusType) {
            "PENDING" -> UploadStatus.Pending
            "PREPROCESSING" -> UploadStatus.Preprocessing("processing")
            "QUEUED" -> UploadStatus.Queued(0)
            "IN_PROGRESS" -> UploadStatus.InProgress(
                progressPercent, bytesUploaded, totalBytes, currentChunk, totalChunks, speedKbps
            )
            "PAUSED" -> UploadStatus.Paused(progressPercent, bytesUploaded)
            "VERIFYING" -> UploadStatus.Verifying(progressPercent)
            "COMPLETED" -> UploadStatus.Completed(remoteUrl ?: "", fileId)
            "FAILED" -> UploadStatus.Failed(errorMessage ?: "Unknown error", retryCount, isRetryable)
            "CANCELLED" -> UploadStatus.Cancelled
            else -> UploadStatus.Pending
        }
    }
}
