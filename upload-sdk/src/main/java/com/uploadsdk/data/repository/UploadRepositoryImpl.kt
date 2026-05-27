package com.uploadsdk.data.repository

import android.content.Context
import androidx.work.Data
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uploadsdk.data.chunk.ChunkEngine
import com.uploadsdk.data.local.UploadDatabase
import com.uploadsdk.data.local.entity.ChunkEntity
import com.uploadsdk.data.local.entity.UploadTaskEntity
import com.uploadsdk.data.preprocessor.FilePreprocessor
import com.uploadsdk.data.scheduler.UploadScheduler
import com.uploadsdk.data.worker.UploadWorkObserver
import com.uploadsdk.data.worker.UploadWorker
import com.uploadsdk.domain.model.*
import com.uploadsdk.domain.repository.UploadRepository
import com.uploadsdk.util.UploadLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: UploadDatabase,
    private val preprocessor: FilePreprocessor,
    private val chunkEngine: ChunkEngine,
    private val scheduler: UploadScheduler,
    private val workObserver: UploadWorkObserver,
    private val config: com.uploadsdk.config.UploadConfig
) : UploadRepository {

    private val taskDao = database.uploadTaskDao()
    private val chunkDao = database.chunkDao()
    private val gson = Gson()

    override suspend fun enqueueUpload(task: UploadTask): String {
        val taskId = task.taskId
        val file = task.file

        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: ${file.absolutePath}")
        }

        // 1. PREPROCESSING
        val preprocessResult = preprocessor.preprocess(task)
        if (!preprocessResult.isEligible) {
            throw IllegalArgumentException(preprocessResult.rejectionReason ?: "File not eligible")
        }

        // 2. Save to persistent queue
        val entity = UploadTaskEntity(
            taskId = taskId,
            filePath = file.absolutePath,
            fileName = task.fileName,
            mimeType = preprocessResult.mimeType,
            totalBytes = file.length(),
            chunkSize = task.chunkSize,
            priority = task.priority.name,
            statusType = "PENDING",
            checksum = preprocessResult.checksum,
            thumbnailPath = preprocessResult.thumbnailPath,
            metadataJson = gson.toJson(preprocessResult.metadata),
            maxRetries = task.maxRetries
        )
        taskDao.insert(entity)

        // 3. Split into chunks
        val chunks = chunkEngine.splitFileIntoChunks(file, task.chunkSize)
        val chunkEntities = chunks.map { chunk ->
            ChunkEntity(
                taskId = taskId,
                chunkIndex = chunk.chunkIndex,
                startByte = chunk.startByte,
                endByte = chunk.endByte,
                size = chunk.size,
                isUploaded = false
            )
        }
        chunkDao.insertAll(chunkEntities)

        // 4. Update task with total chunks
        taskDao.updateProgress(taskId, 0, 0, 0, chunks.size)

        // 5. Schedule via WorkManager
        val inputData = workDataOf(
            UploadWorker.KEY_TASK_ID to taskId,
            UploadWorker.KEY_FILE_PATH to file.absolutePath,
            UploadWorker.KEY_FILE_NAME to task.fileName,
            UploadWorker.KEY_MIME_TYPE to preprocessResult.mimeType,
            UploadWorker.KEY_CHUNK_SIZE to task.chunkSize,
            UploadWorker.KEY_TOTAL_BYTES to file.length(),
            UploadWorker.KEY_TOTAL_CHUNKS to chunks.size,
            UploadWorker.KEY_CHECKSUM to preprocessResult.checksum,
            UploadWorker.KEY_PARALLEL_CHUNKS to config.parallelUploads
        )

        scheduler.scheduleUpload(taskId, task.priority, inputData)
        return taskId
    }

    override suspend fun cancelUpload(taskId: String) {
        scheduler.cancelUpload(taskId)
        taskDao.updateStatus(taskId, "CANCELLED")
    }

    override suspend fun deleteUpload(taskId: String) {
        scheduler.cancelUpload(taskId)
        val entity = taskDao.getById(taskId)
        if (entity != null) {
            cleanupCachedFiles(entity.filePath, entity.thumbnailPath)
        }
        chunkDao.deleteByTaskId(taskId)
        database.sessionDao().deleteByTaskId(taskId)
        taskDao.deleteById(taskId)
    }

    override suspend fun pauseUpload(taskId: String) {
        scheduler.cancelUpload(taskId)
        taskDao.updateStatus(taskId, "PAUSED")
    }

    override suspend fun resumeUpload(taskId: String) {
        val entity = taskDao.getById(taskId) ?: return
        if (entity.statusType != "PAUSED" && entity.statusType != "FAILED") return

        val file = File(entity.filePath)
        if (!file.exists()) {
            taskDao.markFailed(taskId, "File no longer exists")
            return
        }

        val inputData = workDataOf(
            UploadWorker.KEY_TASK_ID to taskId,
            UploadWorker.KEY_FILE_PATH to entity.filePath,
            UploadWorker.KEY_FILE_NAME to entity.fileName,
            UploadWorker.KEY_MIME_TYPE to entity.mimeType,
            UploadWorker.KEY_CHUNK_SIZE to entity.chunkSize,
            UploadWorker.KEY_TOTAL_BYTES to entity.totalBytes,
            UploadWorker.KEY_TOTAL_CHUNKS to entity.totalChunks,
            UploadWorker.KEY_CHECKSUM to (entity.checksum ?: ""),
            UploadWorker.KEY_IS_RESUME to true,
            UploadWorker.KEY_PARALLEL_CHUNKS to config.parallelUploads
        )

        val priority = try {
            UploadPriority.valueOf(entity.priority)
        } catch (e: Exception) {
            UploadPriority.NORMAL
        }

        scheduler.scheduleResume(taskId, inputData)
        taskDao.updateStatus(taskId, "PENDING")
    }

    override suspend fun retryUpload(taskId: String) {
        taskDao.incrementRetry(taskId)
        resumeUpload(taskId)
    }

    override fun observeUpload(taskId: String): Flow<UploadResult> {
        // Combine Room DB state with WorkManager progress for real-time updates
        val dbFlow = taskDao.observeById(taskId).filterNotNull().map { entity ->
            mapEntityToResult(entity)
        }
        val workFlow = workObserver.observeWorkProgress(taskId)
            .map<UploadResult.Progress, UploadResult.Progress?> { it }
            .onStart { emit(null) }

        return dbFlow.combine(workFlow) { dbResult, workResult ->
            // Prefer work progress only for active uploads, use DB for final or paused states
            when (dbResult) {
                is UploadResult.Success,
                is UploadResult.Failure,
                is UploadResult.Cancelled,
                is UploadResult.Paused -> dbResult
                else -> {
                    // Enrich work progress with fileName from database
                    if (workResult != null) {
                        workResult.copy(fileName = dbResult.fileName)
                    } else {
                        dbResult
                    }
                }
            }
        }
    }

    override fun observeAllUploads(): Flow<List<UploadResult>> {
        return taskDao.observeAll().map { entities ->
            entities.map { entity ->
                mapEntityToResult(entity)
            }
        }
    }

    override suspend fun getPendingUploads(): List<UploadTask> {
        return taskDao.getPendingTasks().map { entity ->
            mapEntityToTask(entity)
        }
    }

    override suspend fun clearCompletedUploads() {
        val completed = taskDao.observeAll().first().filter { it.statusType == "COMPLETED" }
        completed.forEach { task ->
            cleanupCachedFiles(task.filePath, task.thumbnailPath)
            chunkDao.deleteByTaskId(task.taskId)
            database.sessionDao().deleteByTaskId(task.taskId)
        }
        taskDao.deleteCompleted()
    }

    override suspend fun getChunkProgress(taskId: String): List<ChunkInfo> {
        return chunkDao.getByTaskId(taskId).map { entity ->
            ChunkInfo(
                chunkIndex = entity.chunkIndex,
                startByte = entity.startByte,
                endByte = entity.endByte,
                isUploaded = entity.isUploaded,
                eTag = entity.eTag,
                checksum = entity.checksum,
                size = entity.size
            )
        }
    }

    override suspend fun getUploadHistory(): List<UploadTask> {
        return taskDao.observeAll().first().map { mapEntityToTask(it) }
    }

    private fun mapEntityToResult(entity: UploadTaskEntity): UploadResult {
        return when (entity.statusType) {
            "PENDING" -> UploadResult.Enqueued(entity.taskId, entity.fileName)
            "PREPROCESSING" -> UploadResult.Preprocessing(entity.taskId, entity.fileName, "processing")
            "QUEUED" -> UploadResult.Enqueued(entity.taskId, entity.fileName)
            "IN_PROGRESS" -> UploadResult.Progress(
                taskId = entity.taskId,
                fileName = entity.fileName,
                percent = entity.progressPercent,
                bytesUploaded = entity.bytesUploaded,
                totalBytes = entity.totalBytes,
                speedKbps = entity.speedKbps
            )
            "PAUSED" -> UploadResult.Paused(entity.taskId, entity.fileName, entity.progressPercent)
            "VERIFYING" -> UploadResult.Progress(
                taskId = entity.taskId,
                fileName = entity.fileName,
                percent = entity.progressPercent,
                bytesUploaded = entity.bytesUploaded,
                totalBytes = entity.totalBytes
            )
            "COMPLETED" -> UploadResult.Success(
                taskId = entity.taskId,
                fileName = entity.fileName,
                remoteUrl = entity.remoteUrl ?: "",
                fileId = entity.fileId,
                bytesUploaded = entity.totalBytes
            )
            "FAILED" -> UploadResult.Failure(
                taskId = entity.taskId,
                fileName = entity.fileName,
                error = entity.errorMessage ?: "Unknown error",
                retryCount = entity.retryCount,
                isRetryable = entity.retryCount < entity.maxRetries
            )
            "CANCELLED" -> UploadResult.Cancelled(entity.taskId, entity.fileName)
            else -> UploadResult.Enqueued(entity.taskId, entity.fileName)
        }
    }

    private fun cleanupCachedFiles(filePath: String, thumbnailPath: String?) {
        try {
            val file = File(filePath)
            if (file.absolutePath.startsWith(context.cacheDir.absolutePath)) {
                file.delete()
            }
            thumbnailPath?.let { File(it).delete() }
        } catch (e: Exception) {
            UploadLogger.e("Failed to cleanup cached files", e)
        }
    }

    private fun mapEntityToTask(entity: UploadTaskEntity): UploadTask {
        val metadata: Map<String, String> = try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(entity.metadataJson, type) ?: emptyMap()
        } catch (e: Exception) { emptyMap() }

        return UploadTask(
            taskId = entity.taskId,
            file = File(entity.filePath),
            fileName = entity.fileName,
            mimeType = entity.mimeType,
            metadata = metadata,
            chunkSize = entity.chunkSize,
            priority = UploadPriority.valueOf(entity.priority),
            maxRetries = entity.maxRetries,
            generateThumbnail = entity.thumbnailPath != null,
            checksum = entity.checksum
        )
    }
}
