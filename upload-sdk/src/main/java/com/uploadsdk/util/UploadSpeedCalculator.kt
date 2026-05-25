package com.uploadsdk.util

import kotlin.math.max

class UploadSpeedCalculator {
    private val samples = mutableListOf<SpeedSample>()
    private var lastBytes = 0L
    private var lastTime = 0L

    data class SpeedSample(val bytesDelta: Long, val timeDeltaMs: Long)

    fun onProgress(bytesUploaded: Long): Double {
        val now = System.currentTimeMillis()
        val bytesDelta = bytesUploaded - lastBytes
        val timeDelta = now - lastTime

        if (timeDelta > 0 && bytesDelta > 0) {
            samples.add(SpeedSample(bytesDelta, timeDelta))
            // Keep only last 5 samples for smoothing
            if (samples.size > 5) samples.removeAt(0)
        }

        lastBytes = bytesUploaded
        lastTime = now

        return calculateAverageSpeed()
    }

    private fun calculateAverageSpeed(): Double {
        if (samples.isEmpty()) return 0.0
        val totalBytes = samples.sumOf { it.bytesDelta }
        val totalTime = samples.sumOf { it.timeDeltaMs }
        return if (totalTime > 0) (totalBytes / 1024.0) / (totalTime / 1000.0) else 0.0
    }

    fun reset() {
        samples.clear()
        lastBytes = 0L
        lastTime = 0L
    }
}
