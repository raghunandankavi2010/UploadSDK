package com.uploadsdk.domain.model

data class SessionInfo(
    val sessionId: String,
    val uploadUrl: String,
    val expiresAt: Long,
    val offset: Long = 0L
)
