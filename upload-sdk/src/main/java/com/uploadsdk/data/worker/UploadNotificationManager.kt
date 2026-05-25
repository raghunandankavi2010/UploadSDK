package com.uploadsdk.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.uploadsdk.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadNotificationManager @Inject constructor(
    private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "upload_sdk_channel"
        const val CHANNEL_NAME = "File Uploads"
        const val NOTIFICATION_ID_BASE = 1000
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of file uploads"
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showProgressNotification(
        taskId: String,
        fileName: String,
        progress: Int,
        bytesUploaded: Long,
        totalBytes: Long
    ) {
        val notificationId = NOTIFICATION_ID_BASE + taskId.hashCode()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Uploading $fileName")
            .setContentText("${formatBytes(bytesUploaded)} / ${formatBytes(totalBytes)}")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    fun showCompleteNotification(taskId: String, fileName: String) {
        val notificationId = NOTIFICATION_ID_BASE + taskId.hashCode()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Upload Complete")
            .setContentText("$fileName uploaded successfully")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    fun showErrorNotification(taskId: String, fileName: String, error: String) {
        val notificationId = NOTIFICATION_ID_BASE + taskId.hashCode()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Upload Failed")
            .setContentText("$fileName: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(taskId: String) {
        val notificationId = NOTIFICATION_ID_BASE + taskId.hashCode()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }
}
