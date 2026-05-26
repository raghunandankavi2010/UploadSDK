package com.uploadsdk.domain.repository

import com.uploadsdk.domain.model.SessionInfo

interface SessionRepository {
    suspend fun createSession(
        taskId: String,
        fileName: String,
        totalBytes: Long,
        totalChunks: Int,
        checksum: String
    ): SessionInfo
    suspend fun refreshSession(sessionId: String): SessionInfo
    suspend fun getSession(taskId: String): SessionInfo?
    suspend fun invalidateSession(taskId: String)
}
