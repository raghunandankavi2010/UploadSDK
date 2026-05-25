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
}
