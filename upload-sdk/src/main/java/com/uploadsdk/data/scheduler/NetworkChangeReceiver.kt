package com.uploadsdk.data.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.uploadsdk.data.local.UploadDatabase
import com.uploadsdk.util.UploadLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NetworkChangeReceiver : BroadcastReceiver() {

    @Inject
    lateinit var database: UploadDatabase

    @Inject
    lateinit var scheduler: UploadScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (isOnline(context)) {
            UploadLogger.d("Network restored - checking for pending uploads")
            CoroutineScope(Dispatchers.IO).launch {
                val pendingTasks = database.uploadTaskDao().getPendingTasks()
                pendingTasks.forEach { task ->
                    if (task.statusType == "PAUSED" || task.statusType == "FAILED") {
                        scheduler.scheduleResume(
                            task.taskId,
                            androidx.work.workDataOf(
                                "task_id" to task.taskId,
                                "file_path" to task.filePath,
                                "file_name" to task.fileName,
                                "mime_type" to task.mimeType,
                                "chunk_size" to task.chunkSize,
                                "total_bytes" to task.totalBytes,
                                "is_resume" to true
                            )
                        )
                    }
                }
            }
        }
    }

    private fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
