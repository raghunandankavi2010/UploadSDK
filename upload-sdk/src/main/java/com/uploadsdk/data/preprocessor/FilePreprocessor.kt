package com.uploadsdk.data.preprocessor

import android.webkit.MimeTypeMap
import com.uploadsdk.domain.model.PreprocessResult
import com.uploadsdk.domain.model.UploadTask
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilePreprocessor @Inject constructor(
    private val checksumCalculator: ChecksumCalculator,
    private val thumbnailGenerator: ThumbnailGenerator,
    private val fileValidator: UploadFileValidator
) {

    suspend fun preprocess(task: UploadTask): PreprocessResult {
        val file = task.file

        val validationResult = fileValidator.validate(file)
        if (validationResult.isFailure) {
            return PreprocessResult(
                "", "",
                isEligible = false,
                rejectionReason = validationResult.exceptionOrNull()?.message ?: "Validation failed"
            )
        }

        // MIME extraction
        val mimeType = extractMimeType(file, task.mimeType)

        // SHA-256 Checksum
        val checksum = checksumCalculator.calculate(file)

        // Thumbnail generation
        val thumbnailPath = if (task.generateThumbnail) {
            thumbnailGenerator.generate(file, mimeType)
        } else null

        // Metadata enrichment
        val metadata = task.metadata.toMutableMap().apply {
            put("original_size", file.length().toString())
            put("extension", file.extension)
            put("preprocessed_at", System.currentTimeMillis().toString())
        }

        return PreprocessResult(
            checksum = checksum,
            mimeType = mimeType,
            thumbnailPath = thumbnailPath,
            isEligible = true,
            metadata = metadata
        )
    }

    private fun extractMimeType(file: File, fallback: String): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: fallback
    }
}
