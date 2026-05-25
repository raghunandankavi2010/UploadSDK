package com.example.uploaddemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.uploaddemo.ui.Screen
import com.example.uploaddemo.ui.screens.UploadDetailScreen
import com.example.uploaddemo.ui.screens.UploadListScreen
import com.example.uploaddemo.ui.theme.UploadDemoTheme
import com.example.uploaddemo.viewmodel.UploadViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: UploadViewModel by viewModels()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris?.let { viewModel.addFiles(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UploadDemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = Screen.UploadList.route
                    ) {
                        composable(Screen.UploadList.route) {
                            UploadListScreen(
                                viewModel = viewModel,
                                onPickFiles = { filePickerLauncher.launch("*/*") },
                                onNavigateToDetail = { taskId ->
                                    navController.navigate(Screen.UploadDetail.createRoute(taskId))
                                }
                            )
                        }
                        composable(Screen.UploadDetail.route) { backStackEntry ->
                            val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
                            UploadDetailScreen(
                                taskId = taskId,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }
}
