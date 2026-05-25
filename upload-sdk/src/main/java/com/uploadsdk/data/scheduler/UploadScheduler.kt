package com.uploadsdk.data.scheduler

import android.content.Context
import androidx.work.*
import com.uploadsdk.data.worker.UploadWorker
import com.uploadsdk.domain.model.UploadPriority
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val batteryConstraint: BatteryAwareConstraint,
    private val thermalMonitor: ThermalThrottlingMonitor
) {

    fun scheduleUpload(
        taskId: String,
        priority: UploadPriority,
        inputData: Data
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(priority != UploadPriority.CRITICAL)
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
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag("upload_$taskId")
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

    fun observeUploadWork(taskId: String): LiveData<WorkInfo> {
        return WorkManager.getInstance(context)
            .getWorkInfoForUniqueWorkLiveData("upload_$taskId")
    }
}
