package com.uploadsdk.di

import com.uploadsdk.config.UploadConfig
import com.uploadsdk.data.remote.api.UploadApiService
import com.uploadsdk.data.remote.interceptor.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://your-api-base-url.com/api/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @RealApi
    fun provideUploadApiService(retrofit: Retrofit): UploadApiService {
        return retrofit.create(UploadApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDefaultUploadApiService(
        @RealApi realApi: UploadApiService,
        @MockApi mockApi: UploadApiService,
        config: UploadConfig
    ): UploadApiService {
        return if (config.useMockApi) mockApi else realApi
    }
}
