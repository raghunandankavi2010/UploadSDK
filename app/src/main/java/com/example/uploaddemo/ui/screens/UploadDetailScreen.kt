package com.example.uploaddemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.uploaddemo.ui.theme.UploadBlue
import com.example.uploaddemo.ui.theme.UploadGreen
import com.example.uploaddemo.ui.theme.UploadGray
import com.example.uploaddemo.viewmodel.UploadDetailViewModel
import com.uploadsdk.util.FileSizeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadDetailScreen(
    taskId: String,
    navController: NavController,
    viewModel: UploadDetailViewModel = hiltViewModel()
) {
    val upload by viewModel.getUpload(taskId).collectAsStateWithLifecycle(initialValue = null)
    val chunks by viewModel.getChunks(taskId).collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(taskId) {
        viewModel.loadTask(taskId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = UploadBlue,
                    titleContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            upload?.let { task ->
                // Task info card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = task.fileName,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoRow("Status", task.statusType)
                        InfoRow("Progress", "${task.progressPercent}%")
                        InfoRow("Size", FileSizeFormatter.format(task.totalBytes))
                        InfoRow("Uploaded", FileSizeFormatter.format(task.bytesUploaded))
                        InfoRow("Chunks", "${task.currentChunk}/${task.totalChunks}")
                        InfoRow("Retries", "${task.retryCount}/${task.maxRetries}")
                        InfoRow("Speed", FileSizeFormatter.formatSpeed(task.speedKbps))
                        if (task.remoteUrl != null) {
                            InfoRow("URL", task.remoteUrl)
                        }
                        if (task.errorMessage != null) {
                            InfoRow("Error", task.errorMessage, isError = true)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Chunks list
                Text(
                    text = "Chunks (${chunks.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(chunks, key = { "${it.taskId}_${it.chunkIndex}" }) { chunk ->
                        ChunkItem(chunk = chunk)
                    }
                }
            } ?: Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, isError: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) androidx.compose.ui.graphics.Color.Red else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ChunkItem(chunk: com.uploadsdk.data.local.entity.ChunkEntity) {
    val statusColor = if (chunk.isUploaded) UploadGreen else UploadGray
    val statusText = if (chunk.isUploaded) "Uploaded" else "Pending"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Chunk ${chunk.chunkIndex}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${FileSizeFormatter.format(chunk.startByte)} - ${FileSizeFormatter.format(chunk.endByte)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(end = 4.dp)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }
        }
    }
}
