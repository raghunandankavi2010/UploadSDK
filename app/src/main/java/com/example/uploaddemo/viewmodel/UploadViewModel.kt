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
            uploadManager.observeAllUploads().collect { results ->
                val current = results.map { result ->
                    UploadUiModel(
                        taskId = result.taskId,
                        fileName = result.fileName,
                        status = mapResultToStatus(result),
                        progress = when (result) {
                            is UploadResult.Progress -> result.percent
                            is UploadResult.Paused -> result.percent
                            else -> 0
                        },
                        bytesUploaded = when (result) {
                            is UploadResult.Progress -> result.bytesUploaded
                            is UploadResult.Success -> result.bytesUploaded
                            else -> 0
                        },
                        totalBytes = when (result) {
                            is UploadResult.Progress -> result.totalBytes
                            else -> 0
                        },
                        speedText = when (result) {
                            is UploadResult.Progress -> FileSizeFormatter.formatSpeed(result.speedKbps)
                            else -> ""
                        }
                    )
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
                // Note: observeTask is no longer needed as init block handles all updates
                // But we still want to emit events for this specific task
                handleSpecificTaskEvents(taskId)
                _selectedFiles.value = _selectedFiles.value.filter { it != uri }
            } catch (e: Exception) {
                _events.emit(UploadEvent.Error(e.message ?: "Upload failed"))
            }
        }
    }

    private fun handleSpecificTaskEvents(taskId: String) {
        viewModelScope.launch {
            uploadManager.observeUpload(taskId).collect { result ->
                when (result) {
                    is UploadResult.Success -> _events.emit(UploadEvent.UploadComplete(taskId, result.remoteUrl))
                    is UploadResult.Failure -> _events.emit(UploadEvent.UploadFailed(taskId, result.error))
                    else -> { }
                }
            }
        }
    }

    private fun mapResultToStatus(result: UploadResult): UploadStatus {
        return when (result) {
            is UploadResult.Enqueued -> UploadStatus.Pending
            is UploadResult.Preprocessing -> UploadStatus.Preprocessing(result.stage)
            is UploadResult.Progress -> UploadStatus.InProgress(
                progressPercent = result.percent,
                bytesUploaded = result.bytesUploaded,
                totalBytes = result.totalBytes,
                currentChunk = 0,
                totalChunks = 0,
                speedKbps = result.speedKbps
            )
            is UploadResult.Paused -> UploadStatus.Paused(result.percent, 0)
            is UploadResult.Success -> UploadStatus.Completed(result.remoteUrl, result.fileId)
            is UploadResult.Failure -> UploadStatus.Failed(result.error, result.retryCount, result.isRetryable)
            is UploadResult.Cancelled -> UploadStatus.Cancelled
        }
    }

    fun pauseUpload(taskId: String) {
        viewModelScope.launch {
            uploadManager.pause(taskId)
        }
    }

    fun resumeUpload(taskId: String) {
        viewModelScope.launch {
            uploadManager.resume(taskId)
        }
    }

    fun cancelUpload(taskId: String) {
        viewModelScope.launch {
            uploadManager.delete(taskId)
            _uploads.value = _uploads.value.filter { it.taskId != taskId }
        }
    }

    fun deleteUpload(taskId: String) {
        viewModelScope.launch {
            uploadManager.delete(taskId)
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
