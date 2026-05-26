package com.uploadsdk.data.chunk

import com.uploadsdk.data.local.dao.ChunkDao
import com.uploadsdk.data.local.entity.ChunkEntity
import com.uploadsdk.data.remote.api.UploadApiService
import com.uploadsdk.data.remote.dto.ChunkUploadResponse
import com.uploadsdk.domain.model.SessionInfo
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChunkUploader @Inject constructor(
    private val apiService: UploadApiService,
    private val chunkDao: ChunkDao
) {

    suspend fun uploadChunk(
        file: File,
        chunk: ChunkEntity,
        taskId: String,
        sessionId: String,
        totalChunks: Int,
        mimeType: String
    ): Result<String> {
        return try {
            val length = (chunk.endByte - chunk.startByte + 1).toInt()
            val buffer = ByteArray(length)
            java.io.RandomAccessFile(file, "r").use { raf ->
                raf.seek(chunk.startByte)
                raf.readFully(buffer)
            }

            val tempFile = java.io.File.createTempFile("chunk_${chunk.chunkIndex}", ".tmp")
            tempFile.writeBytes(buffer)

            val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData(
                "file", "${taskId}_chunk_${chunk.chunkIndex}", requestFile
            )

            val taskIdBody = taskId.toRequestBody("text/plain".toMediaTypeOrNull())
            val chunkIndexBody = chunk.chunkIndex.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val totalChunksBody = totalChunks.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val sessionIdBody = sessionId.toRequestBody("text/plain".toMediaTypeOrNull())

            val response: Response<ChunkUploadResponse> = apiService.uploadChunk(
                file = filePart,
                taskId = taskIdBody,
                chunkIndex = chunkIndexBody,
                totalChunks = totalChunksBody,
                sessionId = sessionIdBody
            )

            tempFile.delete()

            if (response.isSuccessful && response.body()?.success == true) {
                val eTag = response.body()?.eTag ?: "etag_${chunk.chunkIndex}"
                chunkDao.markUploaded(taskId, chunk.chunkIndex, eTag)
                Result.success(eTag)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                chunkDao.markFailed(taskId, chunk.chunkIndex, errorMsg)
                Result.failure(Exception("Chunk upload failed: $errorMsg"))
            }
        } catch (e: Exception) {
            chunkDao.markFailed(taskId, chunk.chunkIndex, e.message ?: "Exception occurred")
            Result.failure(e)
        }
    }
}
