package com.uploadsdk.util

/**
 * Performance benchmark utility for upload operations.
 */
object UploadBenchmark {

    data class Result(
        val taskId: String,
        val fileSize: Long,
        val totalDurationMs: Long,
        val chunkCount: Int,
        val avgChunkDurationMs: Double,
        val throughputKbps: Double,
        val retryCount: Int
    )

    private val results = mutableListOf<Result>()

    fun record(result: Result) {
        results.add(result)
        UploadLogger.d("Benchmark: $result")
    }

    fun getAverageThroughput(): Double {
        if (results.isEmpty()) return 0.0
        return results.map { it.throughputKbps }.average()
    }

    fun getTotalBytesUploaded(): Long {
        return results.sumOf { it.fileSize }
    }

    fun getReport(): String {
        if (results.isEmpty()) return "No benchmark data"
        val avgThroughput = getAverageThroughput()
        val totalBytes = getTotalBytesUploaded()
        val totalTime = results.sumOf { it.totalDurationMs }
        return buildString {
            appendLine("Upload Benchmark Report")
            appendLine("=====================")
            appendLine("Total uploads: ${results.size}")
            appendLine("Total data: ${FileSizeFormatter.format(totalBytes)}")
            appendLine("Total time: ${totalTime / 1000}s")
            appendLine("Average throughput: ${FileSizeFormatter.formatSpeed(avgThroughput)}")
            appendLine("Average chunk time: ${results.map { it.avgChunkDurationMs }.average().toInt()}ms")
            appendLine("Total retries: ${results.sumOf { it.retryCount }}")
        }
    }

    fun clear() {
        results.clear()
    }
}
