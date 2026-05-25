package com.uploadsdk.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor() : Interceptor {
    // In production, inject TokenProvider here
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer YOUR_API_TOKEN")
            .addHeader("X-Client-Version", "1.0.0")
            .addHeader("X-Platform", "Android")
            .build()
        return chain.proceed(request)
    }
}
