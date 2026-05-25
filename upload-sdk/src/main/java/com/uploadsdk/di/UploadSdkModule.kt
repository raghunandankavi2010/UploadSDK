package com.uploadsdk.di

import android.content.Context
import androidx.work.WorkManager
import com.uploadsdk.util.NoOpUploadAnalytics
import com.uploadsdk.util.UploadAnalytics
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UploadSdkModule {

    @Binds
    @Singleton
    abstract fun bindUploadAnalytics(analytics: NoOpUploadAnalytics): UploadAnalytics

    companion object {
        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
            return WorkManager.getInstance(context)
        }
    }
}
