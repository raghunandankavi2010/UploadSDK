package com.uploadsdk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_tasks")
data class UploadTaskEntity(
    @PrimaryKey
    val taskId: String,
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val totalBytes: Long,
    val chunkSize: Int,
    val priority: String, // LOW, NORMAL, HIGH, CRITICAL
    val statusType: String, // PENDING, PREPROCESSING, QUEUED, IN_PROGRESS, PAUSED, COMPLETED, FAILED, CANCELLED, VERIFYING
    val progressPercent: Int = 0,
    val bytesUploaded: Long = 0,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val remoteUrl: String? = null,
    val fileId: String? = null,
    val errorMessage: String? = null,
    val checksum: String? = null,
    val thumbnailPath: String? = null,
    val metadataJson: String = "{}",
    val speedKbps: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
