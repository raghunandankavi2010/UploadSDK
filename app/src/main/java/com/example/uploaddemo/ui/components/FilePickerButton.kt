package com.example.uploaddemo.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FilePickerButton(
    onFilesSelected: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
    mimeType: String = "*/*"
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesSelected(uris)
        }
    }

    IconButton(
        onClick = { launcher.launch(mimeType) },
        modifier = modifier
    ) {
        val icon = when {
            mimeType.startsWith("image/") -> Icons.Default.Image
            mimeType.startsWith("video/") -> Icons.Default.VideoLibrary
            else -> Icons.Default.AttachFile
        }
        Icon(icon, contentDescription = "Pick files")
    }
}
