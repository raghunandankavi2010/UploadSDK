package com.uploadsdk.data.local.dao

import androidx.room.*
import com.uploadsdk.data.local.entity.UploadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: UploadTaskEntity)

    @Query("SELECT * FROM upload_tasks WHERE taskId = :taskId")
    suspend fun getById(taskId: String): UploadTaskEntity?

    @Query("SELECT * FROM upload_tasks WHERE taskId = :taskId")
    fun observeById(taskId: String): Flow<UploadTaskEntity?>

    @Query("SELECT * FROM upload_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<UploadTaskEntity>>

    @Query("SELECT * FROM upload_tasks WHERE statusType IN ('PENDING', 'PREPROCESSING', 'QUEUED', 'IN_PROGRESS', 'PAUSED', 'FAILED') ORDER BY priority DESC, createdAt ASC")
    suspend fun getPendingTasks(): List<UploadTaskEntity>

    @Query("SELECT * FROM upload_tasks WHERE statusType = 'PENDING' ORDER BY priority DESC, createdAt ASC LIMIT 1")
    suspend fun getNextPendingTask(): UploadTaskEntity?

    @Query("DELETE FROM upload_tasks WHERE taskId = :taskId")
    suspend fun deleteById(taskId: String)

    @Query("DELETE FROM upload_tasks WHERE statusType = 'COMPLETED'")
    suspend fun deleteCompleted()

    @Query("UPDATE upload_tasks SET statusType = :status, updatedAt = :timestamp WHERE taskId = :taskId")
    suspend fun updateStatus(taskId: String, status: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE upload_tasks SET progressPercent = :percent, bytesUploaded = :bytes, currentChunk = :current, totalChunks = :total, speedKbps = :speed, updatedAt = :timestamp WHERE taskId = :taskId")
    suspend fun updateProgress(taskId: String, percent: Int, bytes: Long, current: Int, total: Int, speed: Double = 0.0, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE upload_tasks SET retryCount = retryCount + 1, updatedAt = :timestamp WHERE taskId = :taskId")
    suspend fun incrementRetry(taskId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE upload_tasks SET remoteUrl = :url, fileId = :fileId, statusType = 'COMPLETED', progressPercent = 100, updatedAt = :timestamp WHERE taskId = :taskId")
    suspend fun markCompleted(taskId: String, url: String, fileId: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE upload_tasks SET errorMessage = :error, statusType = 'FAILED', updatedAt = :timestamp WHERE taskId = :taskId")
    suspend fun markFailed(taskId: String, error: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE upload_tasks SET thumbnailPath = :path, updatedAt = :timestamp WHERE taskId = :taskId")
    suspend fun updateThumbnail(taskId: String, path: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE upload_tasks SET checksum = :checksum, updatedAt = :timestamp WHERE taskId = :taskId")
    suspend fun updateChecksum(taskId: String, checksum: String, timestamp: Long = System.currentTimeMillis())
}
