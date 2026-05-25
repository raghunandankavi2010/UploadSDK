package com.uploadsdk.domain.model

import java.io.File

data class UploadTask(
    val taskId: String,
    val file: File,
    val fileName: String,
    val mimeType: String,
    val metadata: Map<String, String> = emptyMap(),
    val chunkSize: Int = DEFAULT_CHUNK_SIZE,
    val priority: UploadPriority = UploadPriority.NORMAL,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val generateThumbnail: Boolean = true,
    val checksum: String? = null
) {
    companion object {
        const val DEFAULT_CHUNK_SIZE = 8 * 1024 * 1024 // 8MB chunks as per diagram
        const val DEFAULT_MAX_RETRIES = 3
    }
}
