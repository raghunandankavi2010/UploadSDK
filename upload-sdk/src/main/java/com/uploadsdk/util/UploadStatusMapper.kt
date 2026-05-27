package com.uploadsdk.util

import com.uploadsdk.domain.model.UploadResult

object UploadStatusMapper {

    fun toStatusString(result: UploadResult): String {
        return when (result) {
            is UploadResult.Enqueued -> "PENDING"
            is UploadResult.Preprocessing -> "PREPROCESSING"
            is UploadResult.Progress -> "IN_PROGRESS"
            is UploadResult.Paused -> "PAUSED"
            is UploadResult.Success -> "COMPLETED"
            is UploadResult.Failure -> "FAILED"
            is UploadResult.Cancelled -> "CANCELLED"
        }
    }
}
