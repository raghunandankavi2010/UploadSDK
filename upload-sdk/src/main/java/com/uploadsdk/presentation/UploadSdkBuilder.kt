package com.uploadsdk.presentation

import android.content.Context
import com.uploadsdk.config.UploadConfig

class UploadSdkBuilder(private val context: Context) {
    private var config = UploadConfig(baseUrl = "")

    fun baseUrl(url: String) = apply { config = config.copy(baseUrl = url) }
    fun chunkSize(size: Int) = apply { config = config.copy(chunkSize = size) }
    fun maxRetries(retries: Int) = apply { config = config.copy(maxRetries = retries) }
    fun parallelUploads(count: Int) = apply { config = config.copy(parallelUploads = count) }
    fun enableThumbnail(enable: Boolean) = apply { config = config.copy(enableThumbnail = enable) }
    fun batteryAware(enable: Boolean) = apply { config = config.copy(batteryAware = enable) }
    fun thermalThrottling(enable: Boolean) = apply { config = config.copy(thermalThrottling = enable) }
    fun timeout(ms: Long) = apply { config = config.copy(timeoutMs = ms) }
    fun authProvider(provider: () -> String) = apply { config = config.copy(authTokenProvider = provider) }

    fun build(): UploadSdk {
        require(config.baseUrl.isNotBlank()) { "baseUrl must be set" }
        return UploadSdk.create(context, config)
    }
}
