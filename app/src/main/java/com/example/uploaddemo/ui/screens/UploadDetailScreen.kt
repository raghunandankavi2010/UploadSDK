package com.example.uploaddemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.tooling.preview.Preview
import com.example.uploaddemo.ui.theme.UploadDemoTheme
import com.uploadsdk.data.local.entity.ChunkEntity
import com.uploadsdk.data.local.entity.UploadTaskEntity
import com.uploadsdk.util.FileSizeFormatter

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

    UploadDetailContent(
        upload = upload,
        chunks = chunks,
        onBackClick = { navController.popBackStack() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadDetailContent(
    upload: UploadTaskEntity?,
    chunks: List<ChunkEntity>,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        task.remoteUrl?.let { url ->
                            InfoRow("URL", url)
                        }
                        task.errorMessage?.let { error ->
                            InfoRow("Error", error, isError = true)
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
fun ChunkItem(chunk: ChunkEntity) {
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

@Preview(showBackground = true)
@Composable
fun UploadDetailScreenPreview() {
    UploadDemoTheme {
        val sampleTask = UploadTaskEntity(
            taskId = "1",
            filePath = "/storage/emulated/0/Download/video.mp4",
            fileName = "video.mp4",
            mimeType = "video/mp4",
            totalBytes = 1024 * 1024 * 10,
            chunkSize = 1024 * 1024,
            priority = "NORMAL",
            statusType = "IN_PROGRESS",
            progressPercent = 45,
            bytesUploaded = 1024 * 1024 * 4,
            currentChunk = 4,
            totalChunks = 10,
            speedKbps = 512.0
        )
        val sampleChunks = listOf(
            ChunkEntity("1", 0, 0, 1024 * 1024 - 1, isUploaded = true),
            ChunkEntity("1", 1, 1024 * 1024, 2 * 1024 * 1024 - 1, isUploaded = true),
            ChunkEntity("1", 2, 2 * 1024 * 1024, 3 * 1024 * 1024 - 1, isUploaded = true),
            ChunkEntity("1", 3, 3 * 1024 * 1024, 4 * 1024 * 1024 - 1, isUploaded = false)
        )
        UploadDetailContent(
            upload = sampleTask,
            chunks = sampleChunks,
            onBackClick = {}
        )
    }
}
