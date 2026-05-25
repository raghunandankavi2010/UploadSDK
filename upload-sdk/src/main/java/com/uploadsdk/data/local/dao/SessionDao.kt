package com.uploadsdk.data.local.dao

import androidx.room.*
import com.uploadsdk.data.local.entity.SessionEntity

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM upload_sessions WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: String): SessionEntity?

    @Query("DELETE FROM upload_sessions WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: String)

    @Query("UPDATE upload_sessions SET offset = :offset WHERE taskId = :taskId")
    suspend fun updateOffset(taskId: String, offset: Long)
}
