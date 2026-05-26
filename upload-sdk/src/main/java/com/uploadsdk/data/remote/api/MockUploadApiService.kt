package com.uploadsdk.data.remote.api

import com.uploadsdk.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock implementation of UploadApiService for testing without a real backend.
 * Simulates network delay and returns successful responses.
 */
@Singleton
class MockUploadApiService @Inject constructor() : UploadApiService {

    private val sessions = mutableMapOf<String, InitUploadResponse>()
    private val uploadedChunks = mutableMapOf<String, MutableSet<Int>>()

    override suspend fun initUpload(request: InitUploadRequest): Response<InitUploadResponse> {
        kotlinx.coroutines.delay(500) // Simulate network
        val sessionId = "session_${System.currentTimeMillis()}"
        val response = InitUploadResponse(
            success = true,
            sessionId = sessionId,
            uploadUrl = "https://mock-server.com/upload/$sessionId",
            expiresAt = System.currentTimeMillis() + 3600000
        )
        sessions[request.fileName] = response
        return Response.success(response)
    }

    override suspend fun uploadChunk(
        file: MultipartBody.Part,
        taskId: RequestBody,
        chunkIndex: RequestBody,
        totalChunks: RequestBody,
        sessionId: RequestBody,
        checksum: RequestBody?
    ): Response<ChunkUploadResponse> {
        kotlinx.coroutines.delay(300) // Simulate upload time
        val taskIdStr = taskId.readString()
        val index = chunkIndex.readString().toIntOrNull() ?: 0
        uploadedChunks.getOrPut(taskIdStr) { mutableSetOf() }.add(index)
        return Response.success(
            ChunkUploadResponse(
                success = true,
                eTag = "etag_$index",
                chunkIndex = index
            )
        )
    }

    private fun RequestBody.readString(): String {
        return try {
            val buffer = Buffer()
            this.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: Exception) {
            ""
        }
    }

    override suspend fun commitUpload(request: CommitUploadRequest): Response<CommitUploadResponse> {
        kotlinx.coroutines.delay(500)
        return Response.success(
            CommitUploadResponse(
                success = true,
                remoteUrl = "https://mock-cdn.com/files/${request.fileName}",
                fileId = "file_${System.currentTimeMillis()}"
            )
        )
    }

    override suspend fun getUploadStatus(taskId: String): Response<UploadStatusResponse> {
        return Response.success(
            UploadStatusResponse(
                taskId = taskId,
                status = "in_progress",
                uploadedChunks = uploadedChunks[taskId]?.toList() ?: emptyList()
            )
        )
    }

    override suspend fun refreshSession(request: RefreshSessionRequest): Response<InitUploadResponse> {
        return initUpload(
            InitUploadRequest(
                fileName = "refreshed",
                mimeType = "application/octet-stream",
                totalBytes = 0,
                totalChunks = 0,
                checksum = ""
            )
        )
    }
}
