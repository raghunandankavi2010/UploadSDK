package com.example.uploaddemo.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uploaddemo.ui.components.UploadItem
import com.example.uploaddemo.ui.theme.UploadBlue
import com.example.uploaddemo.ui.theme.UploadGray
import com.example.uploaddemo.ui.theme.UploadGreen
import com.example.uploaddemo.ui.theme.UploadRed
import com.example.uploaddemo.viewmodel.UploadEvent
import com.example.uploaddemo.viewmodel.UploadViewModel
import com.uploadsdk.domain.model.UploadPriority
import com.uploadsdk.domain.model.UploadResult
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadListScreen(
    viewModel: UploadViewModel,
    onPickFiles: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val uploads by viewModel.uploads.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is UploadEvent.UploadComplete -> {
                    snackbarHostState.showSnackbar("Upload complete!")
                }
                is UploadEvent.UploadFailed -> {
                    snackbarHostState.showSnackbar("Upload failed: ${event.error}")
                }
                is UploadEvent.Error -> {
                    snackbarHostState.showSnackbar("Error: ${event.message}")
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload SDK Demo") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = UploadBlue,
                    titleContentColor = Color.White
                ),
                actions = {
                    if (uploads.any { it.result is UploadResult.Success }) {
                        IconButton(onClick = { viewModel.clearCompleted() }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear completed",
                                tint = Color.White
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onPickFiles,
                containerColor = UploadBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add files")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            AnimatedVisibility(visible = selectedFiles.isNotEmpty()) {
                SelectedFilesSection(
                    files = selectedFiles,
                    onRemove = { viewModel.removeFile(it) },
                    onUpload = { uri, priority -> viewModel.uploadFile(uri, priority) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Uploads (${uploads.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (uploads.isEmpty()) {
                EmptyUploadState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(uploads, key = { it.taskId }) { upload ->
                        UploadItem(
                            upload = upload,
                            onPause = { viewModel.pauseUpload(upload.taskId) },
                            onResume = { viewModel.resumeUpload(upload.taskId) },
                            onCancel = { viewModel.cancelUpload(upload.taskId) },
                            onRetry = { viewModel.retryUpload(upload.taskId) },
                            onCompleted = { viewModel.clearCompleted() },
                            onClick = { onNavigateToDetail(upload.taskId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SelectedFilesSection(
    files: List<Uri>,
    onRemove: (Uri) -> Unit,
    onUpload: (Uri, UploadPriority) -> Unit
) {
    var expandedPriority by remember { mutableStateOf<Uri?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Selected Files (${files.size})",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            files.forEach { uri ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uri.lastPathSegment ?: "Unknown",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row {
                        IconButton(onClick = { expandedPriority = uri }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Upload", tint = UploadGreen)
                        }
                        IconButton(onClick = { onRemove(uri) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = UploadRed)
                        }
                    }
                }
                DropdownMenu(
                    expanded = expandedPriority == uri,
                    onDismissRequest = { expandedPriority = null }
                ) {
                    UploadPriority.entries.forEach { priority ->
                        DropdownMenuItem(
                            text = { Text(priority.name) },
                            onClick = {
                                onUpload(uri, priority)
                                expandedPriority = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyUploadState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = UploadGray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No active uploads",
                color = UploadGray,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Tap + to select files",
                color = UploadGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
