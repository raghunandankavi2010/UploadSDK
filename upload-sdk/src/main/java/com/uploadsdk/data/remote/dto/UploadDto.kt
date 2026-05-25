package com.uploadsdk.data.remote.dto

// Request DTOs
data class InitUploadRequest(
    val fileName: String,
    val mimeType: String,
    val totalBytes: Long,
    val totalChunks: Int,
    val checksum: String,
    val metadata: Map<String, String> = emptyMap()
)

data class ChunkUploadRequest(
    val taskId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val checksum: String? = null
)

data class CommitUploadRequest(
    val taskId: String,
    val sessionId: String,
    val fileName: String,
    val mimeType: String,
    val totalChunks: Int,
    val checksum: String,
    val metadata: Map<String, String> = emptyMap()
)

// Response DTOs
data class InitUploadResponse(
    val success: Boolean,
    val sessionId: String,
    val uploadUrl: String,
    val expiresAt: Long,
    val message: String? = null
)

data class ChunkUploadResponse(
    val success: Boolean,
    val eTag: String,
    val chunkIndex: Int,
    val message: String? = null
)

data class CommitUploadResponse(
    val success: Boolean,
    val remoteUrl: String,
    val fileId: String,
    val message: String? = null
)

data class UploadStatusResponse(
    val taskId: String,
    val status: String,
    val uploadedChunks: List<Int>,
    val remoteUrl: String? = null
)
