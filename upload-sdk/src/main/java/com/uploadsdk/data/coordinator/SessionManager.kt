package com.uploadsdk.data.coordinator

import com.uploadsdk.data.local.dao.SessionDao
import com.uploadsdk.data.local.entity.SessionEntity
import com.uploadsdk.data.remote.api.UploadApiService
import com.uploadsdk.data.remote.dto.InitUploadRequest
import com.uploadsdk.data.remote.dto.RefreshSessionRequest
import com.uploadsdk.domain.model.SessionInfo
import com.uploadsdk.domain.repository.SessionRepository
import com.uploadsdk.util.UploadException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val apiService: UploadApiService,
    private val sessionDao: SessionDao
) : SessionRepository {

    override suspend fun createSession(
        taskId: String,
        fileName: String,
        totalBytes: Long,
        totalChunks: Int,
        checksum: String
    ): SessionInfo {
        val request = InitUploadRequest(
            taskId = taskId,
            fileName = fileName,
            mimeType = "application/octet-stream",
            totalBytes = totalBytes,
            totalChunks = totalChunks,
            checksum = checksum
        )
        val response = apiService.initUpload(request)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            val session = SessionInfo(
                sessionId = body.sessionId,
                uploadUrl = body.uploadUrl,
                expiresAt = body.expiresAt
            )
            sessionDao.insert(
                SessionEntity(
                    taskId = taskId,
                    sessionId = session.sessionId,
                    uploadUrl = session.uploadUrl,
                    expiresAt = session.expiresAt
                )
            )
            return session
        }
        throw Exception("Failed to create session: ${response.errorBody()?.string()}")
    }

    override suspend fun refreshSession(sessionId: String): SessionInfo {
        val request = com.uploadsdk.data.remote.dto.RefreshSessionRequest(sessionId, "")
        val response = apiService.refreshSession(request)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            return SessionInfo(
                sessionId = body.sessionId,
                uploadUrl = body.uploadUrl,
                expiresAt = body.expiresAt
            )
        }
        throw Exception("Failed to refresh session")
    }

    override suspend fun getSession(taskId: String): SessionInfo? {
        return sessionDao.getByTaskId(taskId)?.let {
            SessionInfo(
                sessionId = it.sessionId,
                uploadUrl = it.uploadUrl,
                expiresAt = it.expiresAt,
                offset = it.offset
            )
        }
    }

    override suspend fun invalidateSession(taskId: String) {
        sessionDao.deleteByTaskId(taskId)
    }
}