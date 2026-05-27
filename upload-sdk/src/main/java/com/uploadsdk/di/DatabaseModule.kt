package com.uploadsdk.di

import android.content.Context
import androidx.room.Room
import com.uploadsdk.data.local.UploadDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): UploadDatabase {
        return Room.databaseBuilder(
            context,
            UploadDatabase::class.java,
            "upload_sdk_db"
        ).build()
    }

    @Provides
    fun provideUploadTaskDao(db: UploadDatabase) = db.uploadTaskDao()

    @Provides
    fun provideChunkDao(db: UploadDatabase) = db.chunkDao()

    @Provides
    fun provideSessionDao(db: UploadDatabase) = db.sessionDao()
}
