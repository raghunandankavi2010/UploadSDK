package com.uploadsdk.util

interface UploadAnalytics {
    fun trackUploadStart(taskId: String, fileSize: Long, mimeType: String)
    fun trackUploadProgress(taskId: String, percent: Int, speedKbps: Double)
    fun trackUploadSuccess(taskId: String, durationMs: Long, bytesUploaded: Long)
    fun trackUploadFailure(taskId: String, error: String, retryCount: Int)
    fun trackChunkUpload(taskId: String, chunkIndex: Int, durationMs: Long, success: Boolean)
}

class NoOpUploadAnalytics @javax.inject.Inject constructor() : UploadAnalytics {
    override fun trackUploadStart(taskId: String, fileSize: Long, mimeType: String) {}
    override fun trackUploadProgress(taskId: String, percent: Int, speedKbps: Double) {}
    override fun trackUploadSuccess(taskId: String, durationMs: Long, bytesUploaded: Long) {}
    override fun trackUploadFailure(taskId: String, error: String, retryCount: Int) {}
    override fun trackChunkUpload(taskId: String, chunkIndex: Int, durationMs: Long, success: Boolean) {}
}
