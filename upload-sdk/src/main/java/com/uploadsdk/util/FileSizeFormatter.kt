package com.uploadsdk.util

object FileSizeFormatter {
    fun format(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }

    fun formatSpeed(kbps: Double): String {
        if (kbps < 1024) return "%.0f KB/s".format(kbps)
        val mbps = kbps / 1024.0
        return "%.1f MB/s".format(mbps)
    }

    fun formatEta(seconds: Long): String {
        if (seconds < 0) return ""
        if (seconds < 60) return "${seconds}s remaining"
        val minutes = seconds / 60
        val secs = seconds % 60
        if (minutes < 60) return "${minutes}m ${secs}s remaining"
        val hours = minutes / 60
        val mins = minutes % 60
        return "${hours}h ${mins}m remaining"
    }
}
