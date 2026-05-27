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

    private val results = java.util.concurrent.CopyOnWriteArrayList<Result>()

    fun record(result: Result) {
        results.add(result)
        UploadLogger.d("Benchmark: $result")
    }

    fun getAverageThroughput(): Double {
        val snapshot = results.toList()
        if (snapshot.isEmpty()) return 0.0
        return snapshot.map { it.throughputKbps }.average()
    }

    fun getTotalBytesUploaded(): Long {
        return results.toList().sumOf { it.fileSize }
    }

    fun getReport(): String {
        val snapshot = results.toList()
        if (snapshot.isEmpty()) return "No benchmark data"
        val avgThroughput = snapshot.map { it.throughputKbps }.average()
        val totalBytes = snapshot.sumOf { it.fileSize }
        val totalTime = snapshot.sumOf { it.totalDurationMs }
        return buildString {
            appendLine("Upload Benchmark Report")
            appendLine("=====================")
            appendLine("Total uploads: ${snapshot.size}")
            appendLine("Total data: ${FileSizeFormatter.format(totalBytes)}")
            appendLine("Total time: ${totalTime / 1000}s")
            appendLine("Average throughput: ${FileSizeFormatter.formatSpeed(avgThroughput)}")
            appendLine("Average chunk time: ${snapshot.map { it.avgChunkDurationMs }.average().toInt()}ms")
            appendLine("Total retries: ${snapshot.sumOf { it.retryCount }}")
        }
    }

    fun clear() {
        results.clear()
    }
}
