package com.uploadsdk.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "upload_chunks",
    primaryKeys = ["taskId", "chunkIndex"]
)
data class ChunkEntity(
    val taskId: String,
    val chunkIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val size: Long = endByte - startByte + 1,
    val isUploaded: Boolean = false,
    val eTag: String? = null,
    val checksum: String? = null,
    val lastErrorMessage: String? = null
)
