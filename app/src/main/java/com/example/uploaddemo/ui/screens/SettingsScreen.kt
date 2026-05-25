package com.example.uploaddemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.uploaddemo.ui.theme.UploadBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    var chunkSize by remember { mutableStateOf("8") }
    var maxRetries by remember { mutableStateOf("3") }
    var batteryAware by remember { mutableStateOf(true) }
    var enableThumbnail by remember { mutableStateOf(true) }
    var enableNotifications by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SDK Settings") },
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Upload Configuration",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = chunkSize,
                onValueChange = { chunkSize = it.filter { c -> c.isDigit() } },
                label = { Text("Chunk Size (MB)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = maxRetries,
                onValueChange = { maxRetries = it.filter { c -> c.isDigit() } },
                label = { Text("Max Retries") },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text(
                text = "Features",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Battery Aware Uploads")
                Switch(checked = batteryAware, onCheckedChange = { batteryAware = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Generate Thumbnails")
                Switch(checked = enableThumbnail, onCheckedChange = { enableThumbnail = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show Notifications")
                Switch(checked = enableNotifications, onCheckedChange = { enableNotifications = it })
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "About Upload SDK",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Version 1.0.0
Based on Rahul Ray's System Design
Chunked Resumable Uploads with WorkManager",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
