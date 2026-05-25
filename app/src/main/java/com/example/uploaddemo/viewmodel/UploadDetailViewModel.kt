package com.example.uploaddemo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uploadsdk.data.local.UploadDatabase
import com.uploadsdk.data.local.entity.ChunkEntity
import com.uploadsdk.data.local.entity.UploadTaskEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UploadDetailViewModel @Inject constructor(
    private val database: UploadDatabase
) : ViewModel() {

    private val _task = MutableStateFlow<UploadTaskEntity?>(null)
    val task: StateFlow<UploadTaskEntity?> = _task

    fun loadTask(taskId: String) {
        viewModelScope.launch {
            _task.value = database.uploadTaskDao().getById(taskId)
        }
    }

    fun getUpload(taskId: String): Flow<UploadTaskEntity?> {
        return database.uploadTaskDao().observeById(taskId)
    }

    fun getChunks(taskId: String): Flow<List<ChunkEntity>> {
        return kotlinx.coroutines.flow.flow {
            emit(database.chunkDao().getByTaskId(taskId))
        }
    }
}
