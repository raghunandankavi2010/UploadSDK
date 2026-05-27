package com.uploadsdk.domain.model

sealed class UploadResult {
    abstract val taskId: String
    abstract val fileName: String

    data class Enqueued(
        override val taskId: String,
        override val fileName: String = ""
    ) : UploadResult()

    data class Preprocessing(
        override val taskId: String,
        override val fileName: String = "",
        val stage: String
    ) : UploadResult()

    data class Progress(
        override val taskId: String,
        override val fileName: String = "",
        val percent: Int,
        val bytesUploaded: Long,
        val totalBytes: Long,
        val speedKbps: Double = 0.0
    ) : UploadResult()

    data class Success(
        override val taskId: String,
        override val fileName: String = "",
        val remoteUrl: String,
        val fileId: String? = null,
        val bytesUploaded: Long
    ) : UploadResult()

    data class Failure(
        override val taskId: String,
        override val fileName: String = "",
        val error: String,
        val retryCount: Int = 0,
        val isRetryable: Boolean = true
    ) : UploadResult()

    data class Cancelled(
        override val taskId: String,
        override val fileName: String = ""
    ) : UploadResult()

    data class Paused(
        override val taskId: String,
        override val fileName: String = "",
        val percent: Int
    ) : UploadResult()
}
