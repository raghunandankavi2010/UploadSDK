package com.uploadsdk.data.scheduler

import android.content.Context
import androidx.work.*
import com.uploadsdk.config.UploadConfig
import com.uploadsdk.data.worker.UploadWorker
import com.uploadsdk.domain.model.UploadPriority
import com.uploadsdk.util.UploadLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val batteryConstraint: BatteryAwareConstraint,
    private val thermalMonitor: ThermalThrottlingMonitor,
    private val config: UploadConfig
) {

    private fun resolveNetworkType(): NetworkType {
        return when (config.networkType) {
            UploadConfig.NetworkType.WIFI_ONLY,
            UploadConfig.NetworkType.UNMETERED_ONLY -> NetworkType.UNMETERED
            UploadConfig.NetworkType.ANY -> NetworkType.CONNECTED
        }
    }

    fun scheduleUpload(
        taskId: String,
        priority: UploadPriority,
        inputData: Data
    ) {
        if (thermalMonitor.isThermallyThrottled() && priority != UploadPriority.CRITICAL) {
            UploadLogger.d("Thermal throttling active, deferring non-critical upload $taskId")
        }

        val requireBattery = priority != UploadPriority.CRITICAL && batteryConstraint.shouldDefer()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(resolveNetworkType())
            .setRequiresBatteryNotLow(requireBattery)
            .build()

        val backoffPolicy = if (priority == UploadPriority.CRITICAL) {
            BackoffPolicy.LINEAR
        } else {
            BackoffPolicy.EXPONENTIAL
        }

        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(backoffPolicy, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag("upload_$taskId")
            .addTag("upload_priority_${priority.name}")
            .addTag("upload_task")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "upload_$taskId",
            ExistingWorkPolicy.KEEP,
            uploadWorkRequest
        )
    }

    fun scheduleResume(taskId: String, inputData: Data) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(resolveNetworkType())
            .build()

        val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag("upload_$taskId")
            .addTag("upload_task")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "upload_$taskId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelUpload(taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("upload_$taskId")
    }

    fun observeUploadWork(taskId: String): Flow<WorkInfo> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("upload_$taskId")
            .mapNotNull { it.firstOrNull() }
    }
}
