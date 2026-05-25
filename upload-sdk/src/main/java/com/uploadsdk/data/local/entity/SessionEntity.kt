package com.uploadsdk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_sessions")
data class SessionEntity(
    @PrimaryKey
    val taskId: String,
    val sessionId: String,
    val uploadUrl: String,
    val expiresAt: Long,
    val offset: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
