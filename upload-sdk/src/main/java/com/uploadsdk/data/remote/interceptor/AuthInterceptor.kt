package com.uploadsdk.data.remote.interceptor

import com.uploadsdk.config.UploadConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val config: UploadConfig
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
            .addHeader("X-Client-Version", "1.0.0")
            .addHeader("X-Platform", "Android")

        config.authTokenProvider?.invoke()?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}
