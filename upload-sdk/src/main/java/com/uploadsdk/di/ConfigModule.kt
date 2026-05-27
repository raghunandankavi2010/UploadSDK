package com.uploadsdk.di

import com.uploadsdk.config.UploadConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConfigModule {

    @Provides
    @Singleton
    fun provideUploadConfig(): UploadConfig {
        return UploadConfig(
            baseUrl = "http://10.0.2.2:5000/api/v1/",
            chunkSize = 1024 * 1024, // 1MB chunks for faster local testing
            maxRetries = 3,
            parallelUploads = 3,
            enableThumbnail = true,
            batteryAware = true,
            thermalThrottling = true,
            networkType = UploadConfig.NetworkType.ANY,
            timeoutMs = 30000L,
            useMockApi = false
        )
    }
}
