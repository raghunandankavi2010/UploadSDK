package com.uploadsdk.config

data class UploadConfig(
    val baseUrl: String,
    val chunkSize: Int = 8 * 1024 * 1024, // 8MB
    val maxRetries: Int = 3,
    val parallelUploads: Int = 3,
    val enableCompression: Boolean = false,
    val enableThumbnail: Boolean = true,
    val batteryAware: Boolean = true,
    val thermalThrottling: Boolean = true,
    val networkType: NetworkType = NetworkType.ANY,
    val timeoutMs: Long = 30000L,
    val useMockApi: Boolean = false,
    val authTokenProvider: (() -> String)? = null
) {
    enum class NetworkType {
        ANY, WIFI_ONLY, UNMETERED_ONLY
    }
}
