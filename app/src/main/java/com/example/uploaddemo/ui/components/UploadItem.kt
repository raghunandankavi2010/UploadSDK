package com.example.uploaddemo.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.uploaddemo.ui.theme.*
import com.example.uploaddemo.viewmodel.UploadUiModel
import com.uploadsdk.domain.model.UploadStatus
import com.uploadsdk.util.FileSizeFormatter

@Composable
fun UploadItem(
    upload: UploadUiModel,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClick: () -> Unit = {}
) {
    val statusColor = when (upload.status) {
        is UploadStatus.Completed -> UploadGreen
        is UploadStatus.Failed -> UploadRed
        is UploadStatus.Paused -> UploadOrange
        is UploadStatus.Cancelled -> UploadGray
        else -> UploadBlue
    }

    val statusText = when (upload.status) {
        is UploadStatus.Pending -> "Pending"
        is UploadStatus.Preprocessing -> "Preprocessing..."
        is UploadStatus.Queued -> "Queued"
        is UploadStatus.InProgress -> "Uploading ${upload.progress}%"
        is UploadStatus.Paused -> "Paused"
        is UploadStatus.Verifying -> "Verifying..."
        is UploadStatus.Completed -> "Completed"
        is UploadStatus.Failed -> {
            val failed = upload.status as UploadStatus.Failed
            "Failed${if (failed.retryCount > 0) " (Retry ${failed.retryCount})" else ""}"
        }
        is UploadStatus.Cancelled -> "Cancelled"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UploadThumbnail(thumbnailPath = upload.thumbnailPath)
                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = upload.fileName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${FileSizeFormatter.format(upload.bytesUploaded)} / ${FileSizeFormatter.format(upload.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                UploadActions(
                    status = upload.status,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onRetry = onRetry
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (upload.status is UploadStatus.InProgress || upload.status is UploadStatus.Paused) {
                LinearProgressIndicator(
                    progress = { upload.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                    if (upload.speedText.isNotEmpty()) {
                        Text(
                            text = upload.speedText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }

            if (upload.status is UploadStatus.Failed) {
                val error = (upload.status as UploadStatus.Failed).error
                if (error.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = UploadRed,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (upload.status is UploadStatus.Completed) {
                val url = (upload.status as UploadStatus.Completed).remoteUrl
                if (url.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "URL: $url",
                        style = MaterialTheme.typography.bodySmall,
                        color = UploadGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun UploadActions(
    status: UploadStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Row {
        when (status) {
            is UploadStatus.InProgress -> {
                IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = UploadOrange)
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = UploadRed)
                }
            }
            is UploadStatus.Paused -> {
                IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = UploadGreen)
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = UploadRed)
                }
            }
            is UploadStatus.Failed -> {
                val isRetryable = (status as UploadStatus.Failed).isRetryable
                if (isRetryable) {
                    IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = UploadBlue)
                    }
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = UploadGray)
                }
            }
            is UploadStatus.Completed -> {
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = UploadGray)
                }
            }
            else -> {
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = UploadRed)
                }
            }
        }
    }
}
