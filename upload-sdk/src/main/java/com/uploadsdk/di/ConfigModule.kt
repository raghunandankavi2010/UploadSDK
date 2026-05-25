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
            baseUrl = "https://your-api-base-url.com/api/v1/",
            chunkSize = 8 * 1024 * 1024,
            maxRetries = 3,
            parallelUploads = 3,
            enableThumbnail = true,
            batteryAware = true,
            thermalThrottling = true,
            networkType = UploadConfig.NetworkType.ANY,
            timeoutMs = 30000L
        )
    }
}
