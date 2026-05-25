package com.uploadsdk.data.coordinator

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

@Singleton
class RetryCoordinator @Inject constructor() {

    suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        isRetryable: (Throwable) -> Boolean = { true },
        block: suspend () -> T
    ): T {
        var lastException: Throwable? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                lastException = e
                if (!isRetryable(e) || attempt == maxRetries - 1) {
                    throw e
                }
                val backoffMs = (1000 * 2.0.pow(attempt)).toLong()
                delay(min(backoffMs, 30000)) // Cap at 30s
            }
        }
        throw lastException ?: Exception("Unknown error")
    }
}
