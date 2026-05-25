package com.example.uploaddemo.ui

sealed class Screen(val route: String) {
    data object UploadList : Screen("upload_list")
    data object UploadDetail : Screen("upload_detail/{taskId}") {
        fun createRoute(taskId: String) = "upload_detail/$taskId"
    }
    data object Settings : Screen("settings")
}
