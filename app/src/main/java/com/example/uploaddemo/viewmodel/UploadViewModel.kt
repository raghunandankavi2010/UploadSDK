package com.example.uploaddemo.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uploadsdk.data.worker.UploadWorkObserver
import com.uploadsdk.domain.model.UploadPriority
import com.uploadsdk.domain.model.UploadResult
import com.uploadsdk.domain.model.UploadStatus
import com.uploadsdk.presentation.UploadManager
import com.uploadsdk.util.FileSizeFormatter
import com.uploadsdk.util.UploadFileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val application: Application,
    private val uploadManager: UploadManager,
    private val workObserver: UploadWorkObserver
) : ViewModel() {

    private val _uploads = MutableStateFlow<List<UploadUiModel>>(emptyList())
    val uploads: StateFlow<List<UploadUiModel>> = _uploads.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<Uri>>(emptyList())
    val selectedFiles: StateFlow<List<Uri>> = _selectedFiles.asStateFlow()

    private val _events = MutableSharedFlow<UploadEvent>()
    val events: SharedFlow<UploadEvent> = _events.asSharedFlow()

    init {
        // Observe all uploads from database
        viewModelScope.launch {
            uploadManager.observeAllUploads().collect { progresses ->
                val current = _uploads.value.toMutableList()
                progresses.forEach { progress ->
                    val index = current.indexOfFirst { it.taskId == progress.taskId }
                    val uiModel = UploadUiModel(
                        taskId = progress.taskId,
                        fileName = current.find { it.taskId == progress.taskId }?.fileName ?: "Unknown",
                        status = UploadStatus.InProgress(
                            progressPercent = progress.percent,
                            bytesUploaded = progress.bytesUploaded,
                            totalBytes = progress.totalBytes,
                            currentChunk = 0,
                            totalChunks = 0,
                            speedKbps = progress.speedKbps
                        ),
                        progress = progress.percent,
                        bytesUploaded = progress.bytesUploaded,
                        totalBytes = progress.totalBytes,
                        speedText = FileSizeFormatter.formatSpeed(progress.speedKbps)
                    )
                    if (index >= 0) {
                        current[index] = uiModel
                    } else {
                        current.add(uiModel)
                    }
                }
                _uploads.value = current.sortedByDescending { it.status is UploadStatus.InProgress }
            }
        }
    }

    fun addFiles(uris: List<Uri>) {
        _selectedFiles.value = _selectedFiles.value + uris
    }

    fun removeFile(uri: Uri) {
        _selectedFiles.value = _selectedFiles.value.filter { it != uri }
    }

    fun uploadFile(uri: Uri, priority: UploadPriority = UploadPriority.NORMAL) {
        viewModelScope.launch {
            try {
                val file = UploadFileUtils.uriToFile(
                    application.contentResolver,
                    uri,
                    application.cacheDir
                )
                val fileName = UploadFileUtils.getFileName(application.contentResolver, uri)

                val taskId = uploadManager.uploadFile(
                    file = file,
                    fileName = fileName,
                    priority = priority,
                    metadata = mapOf("source" to "demo_app", "uri" to uri.toString())
                )
                _events.emit(UploadEvent.UploadStarted(taskId))
                observeTask(taskId)
                _selectedFiles.value = _selectedFiles.value.filter { it != uri }
            } catch (e: Exception) {
                _events.emit(UploadEvent.Error(e.message ?: "Upload failed"))
            }
        }
    }

    private fun observeTask(taskId: String) {
        viewModelScope.launch {
            // Observe WorkManager for real-time progress
            workObserver.observeWorkProgress(taskId).collect { progress ->
                val current = _uploads.value.toMutableList()
                val index = current.indexOfFirst { it.taskId == taskId }
                val uiModel = UploadUiModel(
                    taskId = taskId,
                    fileName = current.find { it.taskId == taskId }?.fileName ?: "Upload",
                    status = UploadStatus.InProgress(
                        progressPercent = progress.percent,
                        bytesUploaded = progress.bytesUploaded,
                        totalBytes = progress.totalBytes,
                        currentChunk = 0,
                        totalChunks = 0,
                        speedKbps = progress.speedKbps
                    ),
                    progress = progress.percent,
                    bytesUploaded = progress.bytesUploaded,
                    totalBytes = progress.totalBytes,
                    speedText = FileSizeFormatter.formatSpeed(progress.speedKbps)
                )
                if (index >= 0) {
                    current[index] = uiModel
                } else {
                    current.add(uiModel)
                }
                _uploads.value = current.sortedByDescending { it.status is UploadStatus.InProgress }
            }
        }

        viewModelScope.launch {
            // Observe final states from database
            uploadManager.observeUpload(taskId).collect { result ->
                when (result) {
                    is UploadResult.Success -> {
                        val current = _uploads.value.toMutableList()
                        val index = current.indexOfFirst { it.taskId == taskId }
                        if (index >= 0) {
                            current[index] = current[index].copy(
                                status = UploadStatus.Completed(result.remoteUrl, result.fileId)
                            )
                            _uploads.value = current
                        }
                        _events.emit(UploadEvent.UploadComplete(taskId, result.remoteUrl))
                    }
                    is UploadResult.Failure -> {
                        val current = _uploads.value.toMutableList()
                        val index = current.indexOfFirst { it.taskId == taskId }
                        if (index >= 0) {
                            current[index] = current[index].copy(
                                status = UploadStatus.Failed(result.error, 0, result.isRetryable)
                            )
                            _uploads.value = current
                        }
                        _events.emit(UploadEvent.UploadFailed(taskId, result.error))
                    }
                    else -> { }
                }
            }
        }
    }

    fun pauseUpload(taskId: String) {
        viewModelScope.launch {
            uploadManager.pause(taskId)
            updateTaskStatus(taskId, UploadStatus.Paused(0, 0))
        }
    }

    fun resumeUpload(taskId: String) {
        viewModelScope.launch {
            uploadManager.resume(taskId)
        }
    }

    fun cancelUpload(taskId: String) {
        viewModelScope.launch {
            uploadManager.cancel(taskId)
            _uploads.value = _uploads.value.filter { it.taskId != taskId }
        }
    }

    fun retryUpload(taskId: String) {
        viewModelScope.launch {
            uploadManager.retry(taskId)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            uploadManager.clearCompleted()
            _uploads.value = _uploads.value.filter {
                it.status !is UploadStatus.Completed
            }
        }
    }

    private fun updateTaskStatus(taskId: String, status: UploadStatus) {
        val current = _uploads.value.toMutableList()
        val index = current.indexOfFirst { it.taskId == taskId }
        if (index >= 0) {
            current[index] = current[index].copy(status = status)
            _uploads.value = current
        }
    }
}

data class UploadUiModel(
    val taskId: String,
    val fileName: String,
    val status: UploadStatus,
    val progress: Int = 0,
    val bytesUploaded: Long = 0,
    val totalBytes: Long = 0,
    val speedText: String = "",
    val thumbnailPath: String? = null
)

sealed class UploadEvent {
    data class UploadStarted(val taskId: String) : UploadEvent()
    data class UploadComplete(val taskId: String, val remoteUrl: String) : UploadEvent()
    data class UploadFailed(val taskId: String, val error: String) : UploadEvent()
    data class Error(val message: String) : UploadEvent()
}
