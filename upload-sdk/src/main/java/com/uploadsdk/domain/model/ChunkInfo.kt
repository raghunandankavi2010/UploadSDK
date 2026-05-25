package com.uploadsdk.domain.model

data class ChunkInfo(
    val chunkIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val isUploaded: Boolean = false,
    val eTag: String? = null,
    val checksum: String? = null,
    val size: Long = endByte - startByte + 1
)
