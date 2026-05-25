package com.uploadsdk.di

import com.uploadsdk.data.remote.api.MockUploadApiService
import com.uploadsdk.data.remote.api.UploadApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Debug module that provides MockUploadApiService instead of real API.
 * For production, use the regular NetworkModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugNetworkModule {

    @Provides
    @Singleton
    fun provideUploadApiService(): UploadApiService {
        return MockUploadApiService()
    }
}
