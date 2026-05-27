package com.example.uploaddemo.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.uploaddemo.ui.theme.*
import com.example.uploaddemo.viewmodel.UploadUiModel
import com.uploadsdk.domain.model.UploadResult
import com.uploadsdk.util.FileSizeFormatter

@Composable
fun UploadItem(
    upload: UploadUiModel,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onCompleted: () -> Unit,
    onRetry: () -> Unit,
    onClick: () -> Unit = {}
) {
    val statusColor = when (upload.result) {
        is UploadResult.Success -> UploadGreen
        is UploadResult.Failure -> UploadRed
        is UploadResult.Paused -> UploadOrange
        is UploadResult.Cancelled -> UploadGray
        else -> UploadBlue
    }

    val statusText = when (val result = upload.result) {
        is UploadResult.Enqueued -> "Pending"
        is UploadResult.Preprocessing -> "Preprocessing..."
        is UploadResult.Progress -> "Uploading ${upload.progress}%"
        is UploadResult.Paused -> "Paused"
        is UploadResult.Success -> "Completed"
        is UploadResult.Failure ->
            "Error - Tap to Retry${if (result.retryCount > 0) " (${result.retryCount})" else ""}"
        is UploadResult.Cancelled -> "Cancelled - Tap to Retry"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
                    result = upload.result,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onCompleted = onCompleted,
                    onRetry = onRetry
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (upload.result is UploadResult.Progress || upload.result is UploadResult.Paused) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (upload.speedText.isNotEmpty()) {
                            Text(
                                text = upload.speedText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (upload.etaText.isNotEmpty()) {
                            Text(
                                text = upload.etaText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }

            if (upload.result is UploadResult.Failure) {
                val error = (upload.result as UploadResult.Failure).error
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

            if (upload.result is UploadResult.Success) {
                val url = (upload.result as UploadResult.Success).remoteUrl
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
    result: UploadResult,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCompleted: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Row {
        when (result) {
            is UploadResult.Progress -> {
                IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = UploadOrange)
                }
            }
            is UploadResult.Paused -> {
                IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = UploadGreen)
                }
            }
            is UploadResult.Failure -> {
                IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = UploadBlue)
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = UploadGray)
                }
            }
            is UploadResult.Cancelled -> {
                IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = UploadBlue)
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = UploadGray)
                }
            }
            is UploadResult.Success -> {
                IconButton(onClick = onCompleted, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = UploadGray)
                }
            }
            else -> {}
        }
    }
}
