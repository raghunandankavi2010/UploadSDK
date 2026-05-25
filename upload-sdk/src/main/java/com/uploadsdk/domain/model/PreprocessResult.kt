package com.uploadsdk.domain.model

import java.io.File

data class PreprocessResult(
    val checksum: String,
    val mimeType: String,
    val thumbnailPath: String? = null,
    val isEligible: Boolean = true,
    val rejectionReason: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
