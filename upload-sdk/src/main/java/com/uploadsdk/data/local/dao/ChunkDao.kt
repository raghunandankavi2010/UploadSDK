package com.uploadsdk.data.local.dao

import androidx.room.*
import com.uploadsdk.data.local.entity.ChunkEntity

@Dao
interface ChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>)

    @Query("SELECT * FROM upload_chunks WHERE taskId = :taskId ORDER BY chunkIndex ASC")
    suspend fun getByTaskId(taskId: String): List<ChunkEntity>

    @Query("SELECT * FROM upload_chunks WHERE taskId = :taskId AND isUploaded = 0 ORDER BY chunkIndex ASC LIMIT 1")
    suspend fun getNextPendingChunk(taskId: String): ChunkEntity?

    @Query("UPDATE upload_chunks SET isUploaded = 1, eTag = :eTag WHERE taskId = :taskId AND chunkIndex = :chunkIndex")
    suspend fun markUploaded(taskId: String, chunkIndex: Int, eTag: String)

    @Query("SELECT COUNT(*) FROM upload_chunks WHERE taskId = :taskId AND isUploaded = 1")
    suspend fun getUploadedCount(taskId: String): Int

    @Query("SELECT COUNT(*) FROM upload_chunks WHERE taskId = :taskId")
    suspend fun getTotalCount(taskId: String): Int

    @Query("DELETE FROM upload_chunks WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: String)
}
