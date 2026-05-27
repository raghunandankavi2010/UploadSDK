package com.uploadsdk.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.uploadsdk.data.local.dao.ChunkDao
import com.uploadsdk.data.local.dao.SessionDao
import com.uploadsdk.data.local.dao.UploadTaskDao
import com.uploadsdk.data.local.entity.ChunkEntity
import com.uploadsdk.data.local.entity.SessionEntity
import com.uploadsdk.data.local.entity.UploadTaskEntity

@Database(
    entities = [UploadTaskEntity::class, ChunkEntity::class, SessionEntity::class],
    version = 1,
    exportSchema = true
)
abstract class UploadDatabase : RoomDatabase() {
    abstract fun uploadTaskDao(): UploadTaskDao
    abstract fun chunkDao(): ChunkDao
    abstract fun sessionDao(): SessionDao
}
