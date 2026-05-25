package com.uploadsdk.data.preprocessor

import com.uploadsdk.util.UploadException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadFileValidator @Inject constructor() {

    companion object {
        const val MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024 // 2GB
        val ALLOWED_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "mp4", "mov", "avi",
            "pdf", "doc", "docx", "txt", "zip", "rar", "mp3", "wav"
        )
    }

    fun validate(file: File): Result<Unit> {
        return when {
            !file.exists() -> Result.failure(UploadException.FileNotFoundException(file.absolutePath))
            file.length() == 0L -> Result.failure(UploadException.InvalidFileException("File is empty"))
            file.length() > MAX_FILE_SIZE -> Result.failure(
                UploadException.InvalidFileException("File exceeds 2GB limit: ${file.length()}")
            )
            !isExtensionAllowed(file) -> Result.failure(
                UploadException.InvalidFileException("File type not allowed: ${file.extension}")
            )
            else -> Result.success(Unit)
        }
    }

    private fun isExtensionAllowed(file: File): Boolean {
        return file.extension.lowercase() in ALLOWED_EXTENSIONS || ALLOWED_EXTENSIONS.isEmpty()
    }

    fun getValidationRules(): Map<String, Any> {
        return mapOf(
            "maxFileSize" to MAX_FILE_SIZE,
            "maxFileSizeFormatted" to "2 GB",
            "allowedExtensions" to ALLOWED_EXTENSIONS,
            "maxConcurrentUploads" to UploadQueueManager.MAX_CONCURRENT_UPLOADS
        )
    }
}
