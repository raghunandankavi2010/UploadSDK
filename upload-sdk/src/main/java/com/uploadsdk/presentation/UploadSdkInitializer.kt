package com.uploadsdk.presentation

import android.content.Context
import androidx.startup.Initializer
import com.uploadsdk.util.UploadLogger

class UploadSdkInitializer : Initializer<UploadSdkInitializer> {
    override fun create(context: Context): UploadSdkInitializer {
        UploadLogger.d("UploadSDK initialized")
        return this
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}
