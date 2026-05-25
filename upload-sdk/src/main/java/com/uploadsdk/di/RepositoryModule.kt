package com.uploadsdk.di

import com.uploadsdk.data.coordinator.SessionManager
import com.uploadsdk.data.repository.UploadRepositoryImpl
import com.uploadsdk.domain.repository.SessionRepository
import com.uploadsdk.domain.repository.UploadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUploadRepository(impl: UploadRepositoryImpl): UploadRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionManager): SessionRepository
}
