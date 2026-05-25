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
        checksum: String
    ): SessionInfo {
        val request = InitUploadRequest(
            fileName = fileName,
            mimeType = "application/octet-stream",
            totalBytes = totalBytes,
            totalChunks = 0, // Will be updated
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
        // Recover the taskId associated with this sessionId
        val sessionEntity = sessionDao.getBySessionId(sessionId)
            ?: throw UploadException.InvalidFileException("No local session found for ID: $sessionId")

        val request = RefreshSessionRequest(sessionId, sessionEntity.taskId)
        val response = apiService.refreshSession(request)
        val body = response.body()

        if (response.isSuccessful && body?.success == true) {
            val updatedSession = SessionInfo(
                sessionId = body.sessionId,
                uploadUrl = body.uploadUrl,
                expiresAt = body.expiresAt,
                offset = sessionEntity.offset
            )

            // Update local database to keep it in sync
            sessionDao.insert(
                sessionEntity.copy(
                    uploadUrl = updatedSession.uploadUrl,
                    expiresAt = updatedSession.expiresAt
                )
            )

            return updatedSession
        }

        val errorMessage = body?.message ?: response.errorBody()?.string() ?: "Unknown error"
        throw UploadException.ServerException(
            "Failed to refresh session: $errorMessage",
            response.code()
        )
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
