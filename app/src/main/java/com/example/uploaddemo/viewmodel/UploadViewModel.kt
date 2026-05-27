package com.example.uploaddemo.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uploadsdk.domain.model.UploadPriority
import com.uploadsdk.domain.model.UploadResult
import com.uploadsdk.presentation.UploadManager
import com.uploadsdk.util.FileSizeFormatter
import com.uploadsdk.util.UploadFileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val application: Application,
    private val uploadManager: UploadManager
) : ViewModel() {

    val uploads: StateFlow<List<UploadUiModel>> = uploadManager.observeAllUploads()
        .map { results ->
            results.map { result ->
                UploadUiModel(
                    taskId = result.taskId,
                    fileName = result.fileName,
                    result = result,
                    progress = when (result) {
                        is UploadResult.Progress -> result.percent
                        is UploadResult.Paused -> result.percent
                        is UploadResult.Success -> 100
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
                    },
                    etaText = when (result) {
                        is UploadResult.Progress -> FileSizeFormatter.formatEta(result.etaSeconds)
                        else -> ""
                    }
                )
            }.sortedByDescending { it.result is UploadResult.Progress }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedFiles = MutableStateFlow<List<Uri>>(emptyList())
    val selectedFiles: StateFlow<List<Uri>> = _selectedFiles.asStateFlow()

    private val _events = MutableSharedFlow<UploadEvent>()
    val events: SharedFlow<UploadEvent> = _events.asSharedFlow()

    fun addFiles(uris: List<Uri>) {
        _selectedFiles.value += uris
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

    fun pauseUpload(taskId: String) {
        viewModelScope.launch { uploadManager.pause(taskId) }
    }

    fun resumeUpload(taskId: String) {
        viewModelScope.launch { uploadManager.resume(taskId) }
    }

    fun cancelUpload(taskId: String) {
        viewModelScope.launch { uploadManager.delete(taskId) }
    }

    fun deleteUpload(taskId: String) {
        viewModelScope.launch { uploadManager.delete(taskId) }
    }

    fun retryUpload(taskId: String) {
        viewModelScope.launch { uploadManager.retry(taskId) }
    }

    fun clearCompleted() {
        viewModelScope.launch { uploadManager.clearCompleted() }
    }
}

data class UploadUiModel(
    val taskId: String,
    val fileName: String,
    val result: UploadResult,
    val progress: Int = 0,
    val bytesUploaded: Long = 0,
    val totalBytes: Long = 0,
    val speedText: String = "",
    val etaText: String = "",
    val thumbnailPath: String? = null
)

sealed class UploadEvent {
    data class UploadStarted(val taskId: String) : UploadEvent()
    data class UploadComplete(val taskId: String, val remoteUrl: String) : UploadEvent()
    data class UploadFailed(val taskId: String, val error: String) : UploadEvent()
    data class Error(val message: String) : UploadEvent()
}
