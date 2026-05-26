package com.uploadsdk.di

import com.uploadsdk.BuildConfig
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
            baseUrl = "http://10.0.2.2:3000/",
            chunkSize = 2 * 1024 * 1024,
            maxRetries = 3,
            parallelUploads = 3,
            enableThumbnail = true,
            batteryAware = true,
            thermalThrottling = true,
            networkType = UploadConfig.NetworkType.ANY,
            timeoutMs = 30000L,
            useMockApi = BuildConfig.DEBUG
        )
    }
}
