package com.uploadsdk.data.remote.api

import com.uploadsdk.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface UploadApiService {
    @POST("upload/init")
    suspend fun initUpload(@Body request: InitUploadRequest): Response<InitUploadResponse>

    @Multipart
    @POST("upload/chunk")
    suspend fun uploadChunk(
        @Part file: MultipartBody.Part,
        @Part("task_id") taskId: RequestBody,
        @Part("chunk_index") chunkIndex: RequestBody,
        @Part("total_chunks") totalChunks: RequestBody,
        @Part("session_id") sessionId: RequestBody,
        @Part("checksum") checksum: RequestBody? = null
    ): Response<ChunkUploadResponse>

    @POST("upload/commit")
    suspend fun commitUpload(@Body request: CommitUploadRequest): Response<CommitUploadResponse>

    @GET("upload/status/{taskId}")
    suspend fun getUploadStatus(@Path("taskId") taskId: String): Response<UploadStatusResponse>

    @POST("upload/session/refresh")
    suspend fun refreshSession(@Body request: RefreshSessionRequest): Response<InitUploadResponse>
}
